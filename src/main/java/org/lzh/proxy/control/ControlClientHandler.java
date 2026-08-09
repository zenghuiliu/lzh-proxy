package org.lzh.proxy.control;

import org.lzh.proxy.client.Client;
import org.lzh.proxy.client.ClientProxy;
import org.lzh.proxy.management.MetricsRegistry;
import org.lzh.proxy.protocol.ProxyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * 客户端控制（注册）通道处理器：分发服务端下发的控制/数据消息，并负责
 * 空闲心跳与断线重连。由原 ClientDataHandler(register) 与 ClientIdleDataHandler 合并而来。
 */
public class ControlClientHandler extends SimpleChannelInboundHandler<ProxyMessage> {

    private static final Logger log = LoggerFactory.getLogger(ControlClientHandler.class);

    private final Client client;
    private final ClientProxy proxy;
    private final MetricsRegistry metrics;

    public ControlClientHandler(Client client, ClientProxy proxy, MetricsRegistry metrics) {
        this.client = client;
        this.proxy = proxy;
        this.metrics = metrics;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        metrics.controlReconnect();
        log.warn("服务端连接端口 {} 断连，重连中！", client.registerPort());
        client.registerConnect(proxy);
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage msg) {
        switch (msg.type()) {
            case CONNECT -> client.proxyConnect(proxy, msg.serial());
            case TRANSFER -> {
                Channel appChannel = client.registry().clientChannel().get(msg.serial());
                if (appChannel != null && appChannel.isOpen()) {
                    if (msg.data() != null) {
                        metrics.bytesS2c(msg.data().length);
                    }
                    appChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.data()));
                }
            }
            case DISCONNECT -> {
                Channel appChannel = client.registry().clientChannel().get(msg.serial());
                if (appChannel != null && appChannel.isOpen()) {
                    appChannel.close();
                }
            }
            case HEARTBEAT_PONG -> log.debug("{} pong", ctx.channel().remoteAddress());
            default -> log.warn("unexpected control message type {}", msg.type());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event && event.state() == IdleState.ALL_IDLE) {
            ctx.writeAndFlush(ProxyMessage.ping());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
