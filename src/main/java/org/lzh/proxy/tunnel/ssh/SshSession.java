package org.lzh.proxy.tunnel.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.lzh.proxy.config.ServerEndpoint;
import org.lzh.proxy.config.SshAuthConfig;
import org.lzh.proxy.config.SshEndpointConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 单条 SSH 跳板连接的状态机。
 *
 * <pre>
 * DISCONNECTED ─start()─▶ CONNECTING ─established─▶ CONNECTED
 *                              │  ▲                       │
 *                    fail/timeout│  │ retry(delay)         │ sessionClosed
 *                              ▼  │                       ▼
 *                           BACKOFF ◀──────────────────▶ BACKOFF (attempt++)
 *      fatal(AUTH/HOST_KEY) 从任意活动态 ──▶ STOPPED（终态）
 * </pre>
 *
 * <p>状态转移在 {@link #lock} 上串行化；重连由调度线程池驱动（单 pending 任务，
 * 防止重复调度）。重连成功后重放所有转发。</p>
 */
public class SshSession {

    private static final Logger log = LoggerFactory.getLogger(SshSession.class);

    private final SshEndpointConfig config;
    private final SshClient client;
    private final ScheduledExecutorService scheduler;
    private final ReconnectPolicy policy;
    private final LocalForwarder forwarder;

    /** 本地端口 -> 转发配置（重连后重放依据）。 */
    private final ConcurrentHashMap<Integer, ServerEndpoint> forwards = new ConcurrentHashMap<>();

    /** 本地端口 -> 实际绑定地址（用于解除转发）。 */
    private final ConcurrentHashMap<Integer, SshdSocketAddress> forwardEntries = new ConcurrentHashMap<>();

    private final Object lock = new Object();

    private volatile ClientSession session;
    private volatile SshState state = SshState.DISCONNECTED;
    private int attempt;
    private boolean reconnectScheduled;
    private ScheduledFuture<?> reconnectFuture;

    public SshSession(SshEndpointConfig config, SshClient client, ScheduledExecutorService scheduler,
                      ReconnectPolicy policy, LocalForwarder forwarder) {
        this.config = config;
        this.client = client;
        this.scheduler = scheduler;
        this.policy = policy;
        this.forwarder = forwarder;
    }

    public String id() {
        return config.id();
    }

    public SshState state() {
        return state;
    }

    public void start() {
        synchronized (lock) {
            if (state == SshState.STOPPED) {
                return;
            }
            state = SshState.CONNECTING;
        }
        connect();
    }

    private void connect() {
        ClientSession newSession = null;
        synchronized (lock) {
            if (state == SshState.STOPPED || state == SshState.CONNECTED) {
                return;
            }
            state = SshState.CONNECTING;
            reconnectScheduled = false;
        }
        try {
            newSession = client.connect(config.username(), config.host(), config.port())
                    .verify(config.connectTimeoutMs(), TimeUnit.MILLISECONDS)
                    .getSession();
            configureAuth(newSession);
            newSession.auth().verify(config.connectTimeoutMs(), TimeUnit.MILLISECONDS);
            newSession.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE,
                    TimeUnit.SECONDS, config.keepAliveIntervalSec());
            newSession.addSessionListener(new SessionListener() {
                @Override
                public void sessionClosed(Session closed) {
                    onSessionClosed();
                }

                @Override
                public void sessionException(Session sess, Throwable t) {
                    log.debug("ssh[{}] session exception: {}", config.id(), t.getMessage());
                }
            });

            synchronized (lock) {
                if (state == SshState.STOPPED) {
                    newSession.close();
                    return;
                }
                this.session = newSession;
                this.attempt = 0;
                this.state = SshState.CONNECTED;
            }
            applyAllForwards();
            log.info("ssh[{}] connected to {}:{}", config.id(), config.host(), config.port());
        } catch (Exception e) {
            if (newSession != null) {
                try {
                    newSession.close();
                } catch (IOException closeEx) {
                    // 连接失败后的关闭动作忽略
                }
            }
            handleFailure(classifyFailure(e));
        }
    }

    private void configureAuth(ClientSession s) {
        if (config.auth() instanceof SshAuthConfig.PasswordAuth passwordAuth) {
            s.addPasswordIdentity(passwordAuth.password());
        } else if (config.auth() instanceof SshAuthConfig.KeyAuth keyAuth) {
            loadKeyPairs(s, keyAuth);
        }
    }

    private void loadKeyPairs(ClientSession s, SshAuthConfig.KeyAuth keyAuth) {
        try {
            Path path = Path.of(keyAuth.privateKeyPath());
            FilePasswordProvider passwordProvider = (ctx, resource, retry) ->
                    keyAuth.passphrase() == null ? "" : keyAuth.passphrase();
            try (InputStream in = Files.newInputStream(path)) {
                Iterable<KeyPair> pairs = SecurityUtils.loadKeyPairIdentities(
                        s, NamedResource.ofName(path.toString()), in, passwordProvider);
                for (KeyPair pair : pairs) {
                    s.addPublicKeyIdentity(pair);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to load ssh private key " + keyAuth.privateKeyPath(), e);
        }
    }

    private SshFailureKind classifyFailure(Exception e) {
        String msg = String.valueOf(e.getMessage());
        String low = msg.toLowerCase();
        if (low.contains("auth") || low.contains("password") || low.contains("publickey")
                || low.contains("permission") || low.contains("rejected")) {
            return SshFailureKind.AUTH_FAILED;
        }
        if (low.contains("host key") || low.contains("known_hosts") || low.contains("knownhost")) {
            return SshFailureKind.HOST_KEY_MISMATCH;
        }
        return SshFailureKind.TRANSIENT;
    }

    private void handleFailure(SshFailureKind kind) {
        if (kind != SshFailureKind.TRANSIENT) {
            synchronized (lock) {
                state = SshState.STOPPED;
                if (reconnectFuture != null) {
                    reconnectFuture.cancel(false);
                    reconnectFuture = null;
                }
            }
            log.error("ssh[{}] 致命错误[{}]，停止自动重连", config.id(), kind);
            return;
        }
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        synchronized (lock) {
            if (state == SshState.STOPPED || reconnectScheduled) {
                return;
            }
            state = SshState.BACKOFF;
            attempt++;
            long delay = policy.nextDelayMs(attempt);
            reconnectScheduled = true;
            reconnectFuture = scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
            log.warn("ssh[{}] 连接断开，{}ms 后重连（第 {} 次）", config.id(), delay, attempt);
        }
    }

    /** 会话被底层连接关闭时由 SessionListener 回调。 */
    public void onSessionClosed() {
        synchronized (lock) {
            this.session = null;
            clearForwardEntries();
            if (state != SshState.STOPPED) {
                scheduleReconnect();
            }
        }
    }

    public void addForward(ServerEndpoint endpoint) {
        forwards.put(endpoint.port(), endpoint);
        if (state == SshState.CONNECTED) {
            applyForward(endpoint);
        }
    }

    public void removeForward(ServerEndpoint endpoint) {
        forwards.remove(endpoint.port());
        stopForward(endpoint.port());
    }

    private void applyAllForwards() {
        for (ServerEndpoint endpoint : forwards.values()) {
            applyForward(endpoint);
        }
    }

    private void applyForward(ServerEndpoint endpoint) {
        ClientSession s = this.session;
        if (s == null || !s.isOpen()) {
            return;
        }
        try {
            SshdSocketAddress bound = forwarder.start(s, endpoint);
            forwardEntries.put(endpoint.port(), bound);
            log.info("ssh[{}] forward[{} -> {}:{}] active", config.id(), endpoint.port(),
                    endpoint.forwardIp(), endpoint.forwardPort());
        } catch (Exception e) {
            log.error("ssh[{}] forward[{} -> {}:{}] fail: {}", config.id(), endpoint.port(),
                    endpoint.forwardIp(), endpoint.forwardPort(), e.getMessage());
        }
    }

    private void stopForward(int port) {
        ClientSession s = this.session;
        SshdSocketAddress bound = forwardEntries.remove(port);
        if (s != null && bound != null) {
            try {
                s.stopLocalPortForwarding(bound);
            } catch (IOException ignored) {
                log.debug("ssh[{}] stop forward[{}] ignored", config.id(), port);
            }
        }
    }

    private void clearForwardEntries() {
        ClientSession s = this.session;
        if (s != null) {
            for (SshdSocketAddress bound : forwardEntries.values()) {
                try {
                    s.stopLocalPortForwarding(bound);
                } catch (IOException ignored) {
                    // 会话已关闭，忽略
                }
            }
        }
        forwardEntries.clear();
    }

    public void stop() {
        synchronized (lock) {
            state = SshState.STOPPED;
            if (reconnectFuture != null) {
                reconnectFuture.cancel(false);
                reconnectFuture = null;
            }
            reconnectScheduled = false;
            ClientSession s = this.session;
            this.session = null;
            forwardEntries.clear();
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ignored) {
                    // 关闭失败忽略
                }
            }
        }
    }
}
