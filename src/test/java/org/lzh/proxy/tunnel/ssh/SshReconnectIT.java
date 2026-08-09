/*
 * Copyright 2023-2026 lzh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lzh.proxy.tunnel.ssh;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.Test;
import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.config.EndpointType;
import org.lzh.proxy.config.ManagementConfig;
import org.lzh.proxy.config.RegisterConfig;
import org.lzh.proxy.config.Role;
import org.lzh.proxy.config.SecurityConfig;
import org.lzh.proxy.config.ServerEndpoint;
import org.lzh.proxy.config.SshAuthConfig;
import org.lzh.proxy.config.SshEndpointConfig;
import org.lzh.proxy.management.MetricsRegistry;
import org.lzh.proxy.util.NamedThreadFactory;

/**
 * 集成测试：本地 SSH 端口转发在 SSH 服务器重启后自动重建。
 *
 * <p>流程：嵌入式 MINA SSHD 服务器 + 回显目标 -> 建立本地转发 -> 回显通过 ->
 * 停止 SSH 服务器（状态进入 BACKOFF、转发失效）-> 重启 SSH 服务器 ->
 * 自动重连并重放转发 -> 回显再次通过。</p>
 */
class SshReconnectIT {

    @Test
    void localForwardSurvivesSshServerRestart() throws Exception {
        SshServer sshServer = createSshServer(0);
        sshServer.start();
        int sshPort = sshServer.getPort();

        ServerSocket target = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        int targetPort = target.getLocalPort();
        Thread echo = new Thread(() -> {
            while (true) {
                try (Socket s = target.accept()) {
                    s.setSoTimeout(5000);
                    byte[] buf = new byte[4096];
                    int n = s.getInputStream().read(buf);
                    if (n > 0) {
                        s.getOutputStream().write(buf, 0, n);
                        s.getOutputStream().flush();
                    }
                } catch (IOException e) {
                    break;
                }
            }
        });
        echo.setDaemon(true);
        echo.start();

        int forwardPort = freePort();
        AppConfig config = new AppConfig(Role.SERVER,
                new RegisterConfig("127.0.0.1", 0),
                List.of(new ServerEndpoint(EndpointType.SSH, "127.0.0.1", forwardPort, "test", "127.0.0.1", targetPort)),
                List.of(),
                List.of(new SshEndpointConfig("test", "127.0.0.1", sshPort, "test",
                        new SshAuthConfig.PasswordAuth("secret"), 5000, 1, 3)),
                new SecurityConfig(SecurityConfig.HostKeyPolicy.ACCEPT_ALL, Path.of("target", "known_hosts")),
                new ManagementConfig(false, 0));

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, new NamedThreadFactory("it-ssh"));
        MetricsRegistry metrics = new MetricsRegistry();
        SshSessionManager manager = new SshSessionManager(config, scheduler, metrics);
        SshServer sshServer2 = null;
        try {
            manager.start();
            manager.addForward(config.serverEndpoints().get(0));

            awaitTrue(15_000, () -> canConnect(forwardPort), "本地转发端口未就绪");
            assertEcho(forwardPort, "before-restart");

            // 停止 SSH 服务器：状态进入 BACKOFF，转发端口失效
            sshServer.stop();
            awaitTrue(10_000, () -> !canConnect(forwardPort), "SSH 停止后转发端口仍可连接");
            assertThat(manager.snapshotStates().get("test")).isEqualTo(SshState.BACKOFF);

            // 重启：MINA SshServer 不可复用，在同一端口新建一个（等端口释放 + 重试）
            awaitTrue(10_000, () -> !canConnect(sshPort), "SSH 监听端口未释放");
            SshServer replacement = createSshServer(sshPort);
            sshServer2 = replacement;
            awaitTrue(15_000, () -> tryStart(replacement), "SSH 服务器同端口重启失败");
            awaitTrue(30_000, () -> canConnect(forwardPort), "SSH 重连后转发端口未恢复");
            assertEcho(forwardPort, "after-restart");
        } finally {
            manager.stop();
            try {
                sshServer.stop();
            } catch (IOException ignored) {
                // 已停止
            }
            if (sshServer2 != null) {
                try {
                    sshServer2.stop();
                } catch (IOException ignored) {
                    // 已停止
                }
            }
            scheduler.shutdownNow();
            target.close();
        }
    }

    private static SshServer createSshServer(int port) {
        SshServer server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(port);
        server.setPasswordAuthenticator(
                (username, password, session) -> "test".equals(username) && "secret".equals(password));
        server.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Path.of("target", "it-ssh-hostkey.ser")));
        return server;
    }

    /** 尝试启动 SSH 服务器；端口暂未释放时返回 false 供轮询。 */
    private static boolean tryStart(SshServer server) {
        try {
            if (!server.isStarted()) {
                server.start();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return probe.getLocalPort();
        }
    }

    private static boolean canConnect(int port) {
        try (Socket s = new Socket("127.0.0.1", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void assertEcho(int port, String tag) throws Exception {
        byte[] payload = ("echo-" + tag).getBytes(StandardCharsets.UTF_8);
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            out.write(payload);
            out.flush();
            InputStream in = s.getInputStream();
            byte[] received = new byte[payload.length];
            int off = 0;
            while (off < payload.length) {
                int n = in.read(received, off, payload.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
            assertThat(off).as("echo[%s] 收到字节数", tag).isEqualTo(payload.length);
            assertThat(new String(received, 0, off, StandardCharsets.UTF_8))
                    .as("echo[%s] 内容", tag)
                    .isEqualTo(new String(payload, StandardCharsets.UTF_8));
        }
    }

    private static void awaitTrue(long timeoutMs, java.util.function.BooleanSupplier condition, String message)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError(message + "（超时 " + timeoutMs + "ms）");
    }
}
