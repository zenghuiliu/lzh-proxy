package org.lzh.proxy;

import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.core.NettyFactory;
import org.lzh.proxy.core.Schedulers;
import org.lzh.proxy.core.SerialGenerator;
import org.lzh.proxy.forward.TunnelRegistry;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.lifecycle.LifecycleRegistry;
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

        SshSessionManager sshManager = new SshSessionManager(config, schedulers.sshKeepAlive());
        Server server = new Server(config, netty.boss(), netty.worker(), tunnelRegistry, serialGenerator, sshManager);
        Register register = new Register(config, netty.boss(), netty.worker(), tunnelRegistry, server);

        registry.register(netty)
                .register(schedulers)
                .register(sshManager)
                .register(server)
                .register(register);
        registry.start();
    }

    @Override
    public void stop() {
        registry.stop();
    }
}
