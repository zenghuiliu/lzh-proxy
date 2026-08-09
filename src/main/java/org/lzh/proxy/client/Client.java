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
package org.lzh.proxy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.config.Constants;
import org.lzh.proxy.config.ProxyBinding;
import org.lzh.proxy.control.ControlClientHandler;
import org.lzh.proxy.control.RegisterProtocol;
import org.lzh.proxy.forward.AppConnectionHandler;
import org.lzh.proxy.forward.FlowControlHandler;
import org.lzh.proxy.forward.TunnelRegistry;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.management.MetricsRegistry;
import org.lzh.proxy.protocol.ProxyMessage;
import org.lzh.proxy.protocol.ProxyMessageDecoder;
import org.lzh.proxy.protocol.ProxyMessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.HashedWheelTimer;

/**
 * 客户端连接器（替代原静态 Client）。
 *
 * <p>为每条代理绑定建立注册（控制）通道，并在收到 CONNECT 时连接被代理应用。</p>
 */
public class Client implements Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(Client.class);

    private final AppConfig config;
    private final EventLoopGroup eventLoop;
    private final TunnelRegistry registry;
    private final MetricsRegistry metrics;
    private final Bootstrap bootstrap = new Bootstrap();
    private final HashedWheelTimer timer = new HashedWheelTimer();
    private final List<ClientProxy> proxies = new ArrayList<>();

    public Client(AppConfig config, EventLoopGroup eventLoop, TunnelRegistry registry, MetricsRegistry metrics) {
        this.config = config;
        this.eventLoop = eventLoop;
        this.registry = registry;
        this.metrics = metrics;
    }

    public TunnelRegistry registry() {
        return registry;
    }

    public int registerPort() {
        return config.register().port();
    }

    @Override
    public void start() {
        timer.start();
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(eventLoop);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                // 实际 pipeline 在连接建立后按用途添加
            }
        });
        for (ProxyBinding binding : config.proxyBindings()) {
            ClientProxy proxy = new ClientProxy(binding);
            proxies.add(proxy);
            registerConnect(proxy);
        }
    }

    @Override
    public void stop() {
        for (ClientProxy proxy : proxies) {
            Channel register = proxy.registerChannel();
            if (register != null) {
                register.close();
            }
        }
        timer.stop();
    }

    public void registerConnect(ClientProxy proxy) {
        Channel current = proxy.registerChannel();
        if (current != null && current.isOpen()) {
            return;
        }
        bootstrap.connect(config.register().ip().trim(), config.register().port())
                .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
                        Channel registerChannel = future.channel();
                        proxy.registerChannel(registerChannel);
                        if (registerChannel != null && registerChannel.isOpen()) {
                            registerChannel.config().setOption(ChannelOption.SO_KEEPALIVE, true);
                            registerChannel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(64 * 1024, 256 * 1024));
                            registerChannel.pipeline().addLast(new ProxyMessageDecoder(Constants.MAX_FRAME_LENGTH,
                                    Constants.LENGTH_FIELD_OFFSET, Constants.LENGTH_FIELD_LENGTH,
                                    Constants.LENGTH_ADJUSTMENT, Constants.INITIAL_BYTES_TO_STRIP));
                            registerChannel.pipeline().addLast(new ProxyMessageEncoder());
                            registerChannel.pipeline().addLast(new FlowControlHandler(() -> registry.clientChannel().values()));
                            registerChannel.pipeline().addLast(new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS));
                            registerChannel.pipeline().addLast(new ControlClientHandler(Client.this, proxy, metrics));
                            registerChannel.writeAndFlush(ProxyMessage.register(RegisterProtocol.encode(proxy.binding())));
                            log.info("client connected register!");
                        } else {
                            timer.newTimeout(timeout -> registerConnect(proxy), 10, TimeUnit.SECONDS);
                            log.error("client connect register error!");
                        }
                    }
                });
    }

    public void proxyConnect(ClientProxy proxy, long serial) {
        Channel current = proxy.appChannel();
        if (current != null && current.isOpen()) {
            return;
        }
        bootstrap.connect(proxy.binding().appIp().trim(), proxy.binding().appPort())
                .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
                        Channel channel = future.channel();
                        proxy.appChannel(channel);
                        Channel registerChannel = proxy.registerChannel();
                        if (channel != null && channel.isOpen()) {
                            channel.attr(Constants.CHANNEL_SERIAL).set(serial);
                            channel.pipeline().addLast(new AppConnectionHandler(Client.this, proxy, metrics));
                            registry.clientChannel().put(serial, channel);
                            metrics.tunnelOpened();
                            if (registerChannel != null && registerChannel.isOpen()) {
                                registerChannel.writeAndFlush(ProxyMessage.connect(serial));
                            }
                            log.debug("client connected proxy! serial：{}", serial);
                        } else {
                            registry.clientChannel().remove(serial);
                            if (registerChannel != null && registerChannel.isOpen()) {
                                registerChannel.writeAndFlush(ProxyMessage.disconnect(serial));
                            }
                            log.error("client connect proxy error! serial：{}", serial);
                        }
                    }
                });
    }
}
