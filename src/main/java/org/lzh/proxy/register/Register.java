package org.lzh.proxy.register;

import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.config.Constants;
import org.lzh.proxy.forward.TunnelRegistry;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.protocol.ProxyMessageDecoder;
import org.lzh.proxy.protocol.ProxyMessageEncoder;
import org.lzh.proxy.register.handler.RegisterDataHandler;
import org.lzh.proxy.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * 注册（控制）通道监听器（替代原静态 Register）。
 */
public class Register implements Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(Register.class);

    private final AppConfig config;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workGroup;
    private final TunnelRegistry registry;
    private final Server server;
    private final ServerBootstrap serverBootstrap = new ServerBootstrap();
    private volatile Channel serverChannel;

    public Register(AppConfig config, EventLoopGroup bossGroup, EventLoopGroup workGroup, TunnelRegistry registry,
                    Server server) {
        this.config = config;
        this.bossGroup = bossGroup;
        this.workGroup = workGroup;
        this.registry = registry;
        this.server = server;
    }

    @Override
    public void start() {
        serverBootstrap.group(bossGroup, workGroup);
        serverBootstrap.channel(NioServerSocketChannel.class);
        serverBootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        serverBootstrap.childOption(ChannelOption.SO_SNDBUF, 16 * 1024)
                .childOption(ChannelOption.SO_RCVBUF, 16 * 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new ProxyMessageDecoder(Constants.MAX_FRAME_LENGTH, Constants.LENGTH_FIELD_OFFSET,
                        Constants.LENGTH_FIELD_LENGTH, Constants.LENGTH_ADJUSTMENT, Constants.INITIAL_BYTES_TO_STRIP));
                pipeline.addLast(new ProxyMessageEncoder());
                pipeline.addLast(new RegisterDataHandler(registry, server));
            }
        });

        serverBootstrap.bind(config.register().port()).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                Channel channel = future.channel();
                serverChannel = channel;
                if (channel != null && channel.isOpen()) {
                    log.info("{} register started !", channel.localAddress());
                } else {
                    log.error("{} register start error !", channel.localAddress());
                }
            }
        });
    }

    @Override
    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }
    }
}
