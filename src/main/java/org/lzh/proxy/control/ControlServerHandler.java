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
package org.lzh.proxy.control;

import org.lzh.proxy.config.EndpointType;
import org.lzh.proxy.config.ServerEndpoint;
import org.lzh.proxy.forward.TunnelRegistry;
import org.lzh.proxy.management.MetricsRegistry;
import org.lzh.proxy.protocol.ProxyMessage;
import org.lzh.proxy.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * 服务端控制（注册）通道处理器：处理客户端注册、路由数据与控制消息。
 * 原 RegisterDataHandler 重命名，注册报文解析改用 {@link RegisterProtocol}。
 */
public class ControlServerHandler extends SimpleChannelInboundHandler<ProxyMessage> {

    private static final Logger log = LoggerFactory.getLogger(ControlServerHandler.class);

    private final TunnelRegistry registry;
    private final Server server;
    private final MetricsRegistry metrics;

    public ControlServerHandler(TunnelRegistry registry, Server server, MetricsRegistry metrics) {
        this.registry = registry;
        this.server = server;
        this.metrics = metrics;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage msg) {
        Channel reqChannel = ctx.channel();
        switch (msg.type()) {
            case REGISTER -> handleRegister(reqChannel, msg);
            case DISCONNECT -> closeTunnel(msg.serial());
            case TRANSFER -> {
                Channel sndChannel = registry.serverChannel().get(msg.serial());
                if (sndChannel != null && sndChannel.isOpen()) {
                    if (msg.data() != null) {
                        metrics.bytesC2s(msg.data().length);
                    }
                    sndChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.data()));
                }
            }
            case CONNECT -> {
                Channel sndChannel = registry.serverChannel().get(msg.serial());
                if (sndChannel != null && sndChannel.isOpen()) {
                    sndChannel.config().setAutoRead(true);
                }
            }
            case HEARTBEAT_PING -> {
                log.debug("{} ping", reqChannel.remoteAddress());
                reqChannel.writeAndFlush(ProxyMessage.pong());
            }
            default -> log.warn("unexpected control message type {}", msg.type());
        }
    }

    private void closeTunnel(long serial) {
        Channel sndChannel = registry.serverChannel().get(serial);
        if (sndChannel != null && sndChannel.isOpen()) {
            sndChannel.close();
        }
    }

    private void handleRegister(Channel reqChannel, ProxyMessage msg) {
        RegisterProtocol.Registration registration = RegisterProtocol.parse(msg.data()).orElse(null);
        if (registration == null) {
            metrics.registerReject();
            log.warn("注册信息不合法");
            return;
        }
        int reqPort = registration.remotePort();
        Channel oldChannel = registry.proxyToReq().get(reqPort);
        if (oldChannel != null && oldChannel.isOpen()) {
            log.info("请求端口已打开：{}，正在关闭……", reqPort);
            oldChannel.close();
            registry.proxyToReq().remove(reqPort);
        }
        registry.proxyToReq().put(reqPort, reqChannel);
        Channel serverChannel = registry.portToServer().get(reqPort);
        if (serverChannel == null || !serverChannel.isOpen()) {
            ServerEndpoint endpoint = new ServerEndpoint(EndpointType.TCP, "0.0.0.0", reqPort, null, null, null);
            server.bindPort(endpoint);
        }
        log.info("被代理端口已连接到 {}", reqPort);
    }
}
