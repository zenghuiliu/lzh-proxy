package org.lzh.proxy.client.handler;

import org.lzh.proxy.client.Client;
import org.lzh.proxy.client.ClientProxy;
import org.lzh.proxy.config.Constants;
import org.lzh.proxy.management.MetricsRegistry;
import org.lzh.proxy.protocol.ProxyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.Attribute;

/**
 * 客户端数据处理器：注册（控制）通道与应用通道共用。
 */
public class ClientDataHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(ClientDataHandler.class);

    private final Client client;
    private final ClientProxy proxyInfo;
    private final boolean isRegister;
    private final MetricsRegistry metrics;

    public ClientDataHandler(Client client, ClientProxy proxyInfo, boolean isRegister, MetricsRegistry metrics) {
        this.client = client;
        this.proxyInfo = proxyInfo;
        this.isRegister = isRegister;
        this.metrics = metrics;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (isRegister) {
            metrics.controlReconnect();
            log.warn("服务端连接端口 {} 断连，重连中！", client.registerPort());
            client.registerConnect(proxyInfo);
        } else {
            Attribute<Long> serial = channel.attr(Constants.CHANNEL_SERIAL);
            Channel registerChannel = proxyInfo.registerChannel();
            if (registerChannel != null && registerChannel.isOpen()) {
                registerChannel.writeAndFlush(ProxyMessage.disconnect(serial.get()));
            }
            client.registry().clientChannel().remove(serial.get());
            metrics.tunnelClosed();
            log.debug("serial: {},代理关闭", serial.get());
        }
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel channel = ctx.channel();
        if (isRegister) {
            if (msg instanceof ProxyMessage) {
                handlerRegisterData(ctx, (ProxyMessage) msg);
            }
        } else {
            if (msg instanceof ByteBuf) {
                Channel register = proxyInfo.registerChannel();
                if (register != null && register.isOpen()) {
                    Attribute<Long> serial = channel.attr(Constants.CHANNEL_SERIAL);
                    ByteBuf byteBuf = (ByteBuf) msg;
                    byte[] bytes = new byte[byteBuf.readableBytes()];
                    byteBuf.readBytes(bytes);
                    metrics.bytesC2s(bytes.length);
                    register.writeAndFlush(ProxyMessage.transfer(serial.get(), bytes));
                }
            }
        }
    }

    private void handlerRegisterData(ChannelHandlerContext ctx, ProxyMessage msg) {
        if (msg.type() == Constants.TYPE_CONNECT) {
            client.proxyConnect(proxyInfo, msg.serial());
        }
        if (msg.type() == Constants.TYPE_TRANSFER) {
            Channel proxyChannel = client.registry().clientChannel().get(msg.serial());
            if (proxyChannel != null && proxyChannel.isOpen()) {
                if (msg.data() != null) {
                    metrics.bytesS2c(msg.data().length);
                }
                proxyChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.data()));
            }
        }
        if (msg.type() == Constants.TYPE_DISCONNECT) {
            Channel proxyChannel = client.registry().clientChannel().get(msg.serial());
            if (proxyChannel != null && proxyChannel.isOpen()) {
                proxyChannel.close();
            }
        }
        if (msg.type() == Constants.TYPE_HEART_BEET_PONG) {
            log.debug("{} pong", ctx.channel().remoteAddress());
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        super.channelWritabilityChanged(ctx);
    }
}
