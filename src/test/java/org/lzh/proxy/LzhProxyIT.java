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
package org.lzh.proxy;

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
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.config.ManagementConfig;
import org.lzh.proxy.config.ProxyBinding;
import org.lzh.proxy.config.RegisterConfig;
import org.lzh.proxy.config.Role;
import org.lzh.proxy.config.SecurityConfig;
import org.lzh.proxy.config.SshEndpointConfig;
import org.lzh.proxy.config.ServerEndpoint;

/**
 * 公共门面 API 集成测试：通过 LzhProxy 编程方式启动服务端 + 客户端，验证代理链路可用。
 */
class LzhProxyIT {

    @Test
    void embedServerAndClientAndEchoThroughProxy() throws Exception {
        int registerPort = freePort();
        int remotePort = freePort();
        int appPort = freePort();

        ServerSocket echo = new ServerSocket(appPort, 16, InetAddress.getByName("127.0.0.1"));
        Thread echoThread = new Thread(() -> {
            while (true) {
                try (Socket s = echo.accept()) {
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
        echoThread.setDaemon(true);
        echoThread.start();

        AppConfig serverConfig = new AppConfig(Role.SERVER,
                new RegisterConfig("127.0.0.1", registerPort),
                List.of(),
                List.of(),
                List.<SshEndpointConfig>of(),
                new SecurityConfig(SecurityConfig.HostKeyPolicy.ACCEPT_ALL, Path.of("target", "known_hosts")),
                new ManagementConfig(false, 0));

        AppConfig clientConfig = new AppConfig(Role.CLIENT,
                new RegisterConfig("127.0.0.1", registerPort),
                List.<ServerEndpoint>of(),
                List.of(new ProxyBinding("127.0.0.1", appPort, remotePort)),
                List.<SshEndpointConfig>of(),
                new SecurityConfig(SecurityConfig.HostKeyPolicy.ACCEPT_ALL, Path.of("target", "known_hosts")),
                new ManagementConfig(false, 0));

        // 先启动服务端并确认 register 监听就绪，再启动客户端，
        // 避免客户端首次注册连接竞态（失败后要 10s 才重试，超出等待窗口）
        try (LzhProxy.ProxyInstance server = LzhProxy.server().config(serverConfig).start()) {
            awaitTrue(10_000, () -> canConnect(registerPort), "注册端口未就绪");
            try (LzhProxy.ProxyInstance client = LzhProxy.client().config(clientConfig).start()) {

                awaitTrue(20_000, () -> canConnect(remotePort), "代理端口未就绪");

            byte[] payload = "hello-from-embed".getBytes(StandardCharsets.UTF_8);
            try (Socket s = new Socket("127.0.0.1", remotePort)) {
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
                assertThat(off).isEqualTo(payload.length);
                assertThat(new String(received, 0, off, StandardCharsets.UTF_8))
                        .isEqualTo(new String(payload, StandardCharsets.UTF_8));
            }
            }
        } finally {
            echo.close();
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

    private static void awaitTrue(long timeoutMs, BooleanSupplier condition, String message)
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
