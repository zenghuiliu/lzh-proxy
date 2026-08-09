package org.lzh.proxy.server.ssh;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.config.ServerEndpoint;
import org.lzh.proxy.config.SshAuthConfig;
import org.lzh.proxy.config.SshEndpointConfig;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * SSH 跳板会话管理（Phase 1：JSch 实现，Phase 3 由 Apache MINA SSHD 状态机重写）。
 *
 * <p>替代原静态 SSHClient：实例注入配置与调度线程池，保活由调度器驱动。
 * 注意：Phase 1 仍为密码认证；私钥认证与主机密钥校验由 Phase 3 提供。</p>
 */
public class SSHClient implements Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(SSHClient.class);

    private static final int DEFAULT_KEEPALIVE_INTERVAL_MS = 1000;

    private final AppConfig config;
    private final ScheduledExecutorService scheduler;
    private final JSch jSch = new JSch();
    private final ConcurrentHashMap<String, Session> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, List<ServerEndpoint>> forwardMap = new ConcurrentHashMap<>();

    public SSHClient(AppConfig config, ScheduledExecutorService scheduler) {
        this.config = config;
        this.scheduler = scheduler;
    }

    @Override
    public void start() {
        List<SshEndpointConfig> sshInfos = config.sshEndpoints();
        for (SshEndpointConfig sshInfo : sshInfos) {
            connectSession(sshInfo);
        }
    }

    @Override
    public void stop() {
        disconnect();
    }

    public Boolean addSession(String id, Session session) {
        removeSession(id);
        sessionMap.put(id, session);
        return true;
    }

    public Boolean removeSession(String id) {
        Session session = sessionMap.get(id);
        if (session != null) {
            Object userInfo = session.getUserInfo();
            if (userInfo instanceof JschUserInfo jui) {
                jui.setKeepAliveFlag(false);
            }
            session.disconnect();
            sessionMap.remove(id);
        }
        return true;
    }

    public Boolean addForward(ServerEndpoint serverInfo) {
        removeForward(serverInfo);
        if (serverInfo == null || isBlank(serverInfo.sshId())) {
            log.warn("addForward parameters is invalid");
            return false;
        }
        try {
            Session session = getSession(serverInfo.sshId());
            if (session == null) {
                log.warn("not found session[{}]！", serverInfo.sshId());
                return false;
            }
            forwardLocal(session, serverInfo.port(), serverInfo.forwardIp(), serverInfo.forwardPort());
            List<ServerEndpoint> serverInfos = forwardMap.computeIfAbsent(serverInfo.sshId(), k -> new ArrayList<>());
            serverInfos.add(serverInfo);
        } catch (Exception e) {
            log.error("session[{}] forward[ip:{};port:{}] fail.", serverInfo.sshId(), serverInfo.forwardIp(),
                    serverInfo.forwardPort(), e);
        }
        return true;
    }

    public Boolean removeForward(ServerEndpoint serverInfo) {
        Session session = sessionMap.get(serverInfo.sshId());
        if (session != null && session.isConnected()) {
            try {
                String[] pfls = session.getPortForwardingL();
                if (pfls != null) {
                    for (String pfl : pfls) {
                        String[] temp = pfl.split(":");
                        if (temp[0].equals(Integer.toString(serverInfo.port()))) {
                            session.delPortForwardingL(serverInfo.port());
                        }
                    }
                }
            } catch (JSchException e) {
                log.info("delete forward[ip:{};port:{}] exception", serverInfo.forwardIp(),
                        serverInfo.forwardPort(), e);
            }
        }
        List<ServerEndpoint> serverInfos = forwardMap.get(serverInfo.sshId());
        if (serverInfos != null) {
            Iterator<ServerEndpoint> iterator = serverInfos.iterator();
            while (iterator.hasNext()) {
                ServerEndpoint info = iterator.next();
                if (serverInfo.sshId().equals(info.sshId())
                        && serverInfo.forwardIp().equals(info.forwardIp())
                        && serverInfo.forwardPort().equals(info.forwardPort())) {
                    log.info("remove server info!");
                    iterator.remove();
                }
            }
        }
        return true;
    }

    public Boolean isExistSession(String id) {
        return !isBlank(id) && sessionMap.containsKey(id);
    }

    public Session getSession(String id) {
        Session session = sessionMap.get(id);
        if (session != null) {
            if (session.isConnected()) {
                return session;
            }
            removeSession(id);
        }
        return null;
    }

    public Session connectSession(SshEndpointConfig sshInfo) {
        try {
            Session session = sessionMap.get(sshInfo.id());
            if (session != null && session.isConnected()) {
                return session;
            }

            session = jSch.getSession(sshInfo.username(), sshInfo.host(), sshInfo.port());
            String password = passwordOf(sshInfo);
            if (password == null) {
                log.warn("ssh[{}] 私钥认证将在 Phase 3 支持，当前跳过", sshInfo.id());
                return null;
            }
            session.setPassword(password);
            // 关闭主机密钥确认提示（Phase 3 提供主机密钥校验）
            session.setConfig("StrictHostKeyChecking", "no");

            JschUserInfo jschUserInfo = new JschUserInfo();
            jschUserInfo.setKeepAliveFlag(true);
            jschUserInfo.setSshInfo(sshInfo);
            session.setUserInfo(jschUserInfo);

            session.connect(sshInfo.connectTimeoutMs());

            addSession(sshInfo.id(), session);

            sessionKeepAlive(session);
            return session;
        } catch (Exception exception) {
            log.error("ssh连接失败:id[{}];ip[{}];port:{}", sshInfo.id(), sshInfo.host(), sshInfo.port(), exception);
        }
        return null;
    }

    private static String passwordOf(SshEndpointConfig sshInfo) {
        if (sshInfo.auth() instanceof SshAuthConfig.PasswordAuth passwordAuth) {
            return passwordAuth.password();
        }
        return null;
    }

    private void sessionKeepAlive(Session session) {
        if (session == null) {
            return;
        }
        JschUserInfo userInfo = (JschUserInfo) session.getUserInfo();
        synchronized (userInfo.getSshInfo().id().intern()) {
            if (!Boolean.TRUE.equals(userInfo.getKeepAliveFlag())) {
                return;
            }
            if (session.isConnected()) {
                try {
                    session.sendKeepAliveMsg();
                } catch (Exception e) {
                    log.error("session[{}] keep alive fail !", userInfo.getSshInfo().id(), e);
                }
            } else {
                reInitSession(session);
            }
            // 始终重新调度：断连重连失败后继续重试（Phase 3 以退避状态机替代）
            scheduler.schedule(() -> sessionKeepAlive(session), DEFAULT_KEEPALIVE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    public void reInitSession(Session session) {
        JschUserInfo userInfo = (JschUserInfo) session.getUserInfo();
        connectSession(userInfo.getSshInfo());
        List<ServerEndpoint> serverInfos = forwardMap.get(userInfo.getSshInfo().id());
        if (serverInfos != null) {
            Iterator<ServerEndpoint> infos = serverInfos.iterator();
            while (infos.hasNext()) {
                ServerEndpoint info = infos.next();
                addForward(info);
            }
        }
    }

    public Boolean forwardLocal(Session session, Integer localPort, String forwardHost, Integer forwardPort) {
        try {
            if (session != null && session.isConnected()) {
                session.setPortForwardingL(localPort, forwardHost, forwardPort);
                return true;
            }
        } catch (Exception e) {
            log.error("forward local[{} -> {}:{}] fail", localPort, forwardHost, forwardPort, e);
        }
        return false;
    }

    public void disconnect() {
        sessionMap.forEach((key, session) -> session.disconnect());
        sessionMap.clear();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
