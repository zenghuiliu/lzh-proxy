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
