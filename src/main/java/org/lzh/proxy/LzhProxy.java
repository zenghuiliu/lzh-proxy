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

import java.util.Map;
import java.util.Objects;

import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.config.ProxyConfigLoader;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.util.Exceptions;

/**
 * lzh-proxy 公共门面 API：以编程方式启动/停止服务端或客户端代理实例。
 *
 * <p>作为库嵌入使用的最小示例：</p>
 *
 * <pre>{@code
 * // 方式一：从 YAML profile 加载配置并启动
 * try (LzhProxy.ProxyInstance server = LzhProxy.server().configFile("server").start()) {
 *     // ... 你的业务代码
 * }
 *
 * // 方式二：程序化构建 AppConfig 并启动客户端
 * AppConfig clientConfig = new AppConfig(
 *         Role.CLIENT,
 *         new RegisterConfig("1.2.3.4", 7000),
 *         List.of(),
 *         List.of(new ProxyBinding("127.0.0.1", 7002, 7001)),
 *         List.of(),
 *         new SecurityConfig(SecurityConfig.HostKeyPolicy.TOFU_KNOWN_HOSTS, Path.of(".lzh-proxy", "known_hosts")),
 *         new ManagementConfig(false, 0));
 * try (LzhProxy.ProxyInstance client = LzhProxy.client().config(clientConfig).start()) {
 *     // ...
 * }
 * }</pre>
 *
 * <p>线程模型：代理组件在后台线程运行（Netty 事件循环等均为守护线程），
 * {@link ProxyInstance#stop()} 触发有序优雅停机。若需要代理线程维持 JVM 存活，
 * 请在嵌入代码中自行阻塞主线程。</p>
 */
public final class LzhProxy {

    private LzhProxy() {
    }

    /** 构建服务端代理实例。 */
    public static ServerBuilder server() {
        return new ServerBuilder();
    }

    /** 构建客户端代理实例。 */
    public static ClientBuilder client() {
        return new ClientBuilder();
    }

    /** 服务端构建器。 */
    public static final class ServerBuilder {

        private AppConfig config;

        private ServerBuilder() {
        }

        /** 使用程序化构建的配置。 */
        public ServerBuilder config(AppConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            return this;
        }

        /** 从 profile 加载配置（application-&lt;profile&gt;.yml）。 */
        public ServerBuilder configFile(String profile) {
            this.config = ProxyConfigLoader.load(profile);
            return this;
        }

        /** 从 profile 加载配置，并注入环境变量快照与 CLI 密钥覆盖（测试/安全场景）。 */
        public ServerBuilder configFile(String profile, Map<String, String> env, Map<String, String> cliOverrides) {
            this.config = ProxyConfigLoader.load(profile, env, cliOverrides);
            return this;
        }

        /** 启动服务端，返回可 {@link ProxyInstance#stop()} 的运行实例。 */
        public ProxyInstance start() {
            Objects.requireNonNull(config, "请先通过 config()/configFile() 提供配置");
            return launch(new ServerLauncher(config));
        }
    }

    /** 客户端构建器。 */
    public static final class ClientBuilder {

        private AppConfig config;

        private ClientBuilder() {
        }

        /** 使用程序化构建的配置。 */
        public ClientBuilder config(AppConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            return this;
        }

        /** 从 profile 加载配置（application-&lt;profile&gt;.yml）。 */
        public ClientBuilder configFile(String profile) {
            this.config = ProxyConfigLoader.load(profile);
            return this;
        }

        /** 从 profile 加载配置，并注入环境变量快照与 CLI 密钥覆盖。 */
        public ClientBuilder configFile(String profile, Map<String, String> env, Map<String, String> cliOverrides) {
            this.config = ProxyConfigLoader.load(profile, env, cliOverrides);
            return this;
        }

        /** 启动客户端，返回可 {@link ProxyInstance#stop()} 的运行实例。 */
        public ProxyInstance start() {
            Objects.requireNonNull(config, "请先通过 config()/configFile() 提供配置");
            return launch(new ClientLauncher(config));
        }
    }

    private static ProxyInstance launch(Lifecycle launcher) {
        try {
            launcher.start();
        } catch (Exception e) {
            try {
                launcher.stop();
            } catch (Exception ignored) {
                // 启动失败后的清理
            }
            throw Exceptions.wrap(e);
        }
        return new ProxyInstance(launcher);
    }

    /** 运行中的代理实例（AutoCloseable，可用 try-with-resources）。 */
    public static final class ProxyInstance implements AutoCloseable {

        private final Lifecycle launcher;

        private ProxyInstance(Lifecycle launcher) {
            this.launcher = launcher;
        }

        /** 有序优雅停机。 */
        public void stop() {
            launcher.stop();
        }

        @Override
        public void close() {
            stop();
        }
    }
}
