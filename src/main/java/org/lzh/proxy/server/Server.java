package org.lzh.proxy.server;

import java.util.List;

import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.config.EndpointType;
import org.lzh.proxy.config.ServerEndpoint;
import org.lzh.proxy.core.SerialGenerator;
import org.lzh.proxy.forward.TunnelRegistry;
import org.lzh.proxy.forward.UserConnectionHandler;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.management.MetricsRegistry;
import org.lzh.proxy.tunnel.ssh.SshSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * 服务端暴露端口监听器（替代原静态 Server）。
 *
 * <p>管理 Netty 代理链路（TCP 类型）与 SSH 转发（SSH 类型），
 * 由组合根注入配置、事件循环与共享注册表。</p>
 */
public class Server implements Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final AppConfig config;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workGroup;
    private final TunnelRegistry registry;
    private final SerialGenerator serial;
    private final SshSessionManager sshManager;
    private final MetricsRegistry metrics;
    private final ServerBootstrap serverBootstrap = new ServerBootstrap();

    public Server(AppConfig config, EventLoopGroup bossGroup, EventLoopGroup workGroup, TunnelRegistry registry,
                  SerialGenerator serial, SshSessionManager sshManager, MetricsRegistry metrics) {
        this.config = config;
        this.bossGroup = bossGroup;
        this.workGroup = workGroup;
        this.registry = registry;
        this.serial = serial;
        this.sshManager = sshManager;
        this.metrics = metrics;
    }

    @Override
    public void start() {
        serverBootstrap.group(bossGroup, workGroup);
        serverBootstrap.channel(NioServerSocketChannel.class);
        // tcp连接队列长度
        serverBootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        // SO_SNDBUF发送缓冲区，SO_RCVBUF接收缓冲区，SO_KEEPALIVE开启TCP连接保持
        serverBootstrap.childOption(ChannelOption.SO_SNDBUF, 16 * 1024)
                .childOption(ChannelOption.SO_RCVBUF, 16 * 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new UserConnectionHandler(registry, serial, metrics));
            }
        });
        List<ServerEndpoint> serverInfos = config.serverEndpoints();
        for (ServerEndpoint serverInfo : serverInfos) {
            if (serverInfo.type() == EndpointType.TCP) {
                bindPort(serverInfo);
            } else if (serverInfo.type() == EndpointType.SSH) {
                sshManager.addForward(serverInfo);
            }
        }
    }

    @Override
    public void stop() {
        registry.portToServer().values().forEach(Channel::close);
        registry.portToServer().clear();
    }

    public void bindPort(ServerEndpoint serverInfo) {
        serverBootstrap.bind(serverInfo.port()).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                Channel channel = future.channel();
                if (channel != null && channel.isOpen()) {
                    registry.portToServer().put(serverInfo.port(), channel);
                    log.info("{} server started !", channel.localAddress());
                } else {
                    log.error("{} server start error !", channel.localAddress());
                }
            }
        });
    }
}
