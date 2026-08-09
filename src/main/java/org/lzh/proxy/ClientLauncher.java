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

import org.lzh.proxy.client.Client;
import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.core.NettyFactory;
import org.lzh.proxy.forward.TunnelRegistry;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.lifecycle.LifecycleRegistry;
import org.lzh.proxy.management.AdminHttpServer;
import org.lzh.proxy.management.MetricsRegistry;

/**
 * 客户端组合根：装配事件循环与客户端连接器，编排生命周期。
 */
public class ClientLauncher implements Lifecycle {

    private final AppConfig config;
    private final LifecycleRegistry registry = new LifecycleRegistry();

    public ClientLauncher(AppConfig config) {
        this.config = config;
    }

    @Override
    public void start() throws Exception {
        NettyFactory netty = new NettyFactory();
        TunnelRegistry tunnelRegistry = new TunnelRegistry();
        MetricsRegistry metrics = new MetricsRegistry();

        Client client = new Client(config, netty.boss(), tunnelRegistry, metrics);
        AdminHttpServer admin = new AdminHttpServer(config, metrics, tunnelRegistry, null);

        registry.register(netty)
                .register(client)
                .register(admin);
        registry.start();
    }

    @Override
    public void stop() {
        registry.stop();
    }
}
