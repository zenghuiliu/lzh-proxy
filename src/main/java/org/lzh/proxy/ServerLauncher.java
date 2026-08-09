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

import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.core.NettyFactory;
import org.lzh.proxy.core.Schedulers;
import org.lzh.proxy.core.SerialGenerator;
import org.lzh.proxy.forward.TunnelRegistry;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.lifecycle.LifecycleRegistry;
import org.lzh.proxy.management.AdminHttpServer;
import org.lzh.proxy.management.MetricsRegistry;
import org.lzh.proxy.register.Register;
import org.lzh.proxy.server.Server;
import org.lzh.proxy.tunnel.ssh.SshSessionManager;

/**
 * 服务端组合根：装配事件循环、SSH 会话、服务端监听与注册监听，编排生命周期。
 */
public class ServerLauncher implements Lifecycle {

    private final AppConfig config;
    private final LifecycleRegistry registry = new LifecycleRegistry();

    public ServerLauncher(AppConfig config) {
        this.config = config;
    }

    @Override
    public void start() throws Exception {
        NettyFactory netty = new NettyFactory();
        Schedulers schedulers = new Schedulers();
        TunnelRegistry tunnelRegistry = new TunnelRegistry();
        SerialGenerator serialGenerator = new SerialGenerator();
        MetricsRegistry metrics = new MetricsRegistry();

        SshSessionManager sshManager = new SshSessionManager(config, schedulers.sshKeepAlive(), metrics);
        Server server = new Server(config, netty.boss(), netty.worker(), tunnelRegistry, serialGenerator, sshManager,
                metrics);
        Register register = new Register(config, netty.boss(), netty.worker(), tunnelRegistry, server, metrics);
        AdminHttpServer admin = new AdminHttpServer(config, metrics, tunnelRegistry, sshManager);

        registry.register(netty)
                .register(schedulers)
                .register(sshManager)
                .register(server)
                .register(register)
                .register(admin);
        registry.start();
    }

    @Override
    public void stop() {
        registry.stop();
    }
}
