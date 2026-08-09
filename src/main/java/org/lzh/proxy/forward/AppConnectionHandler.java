package org.lzh.proxy.forward;

import org.lzh.proxy.client.Client;
import org.lzh.proxy.client.ClientProxy;
import org.lzh.proxy.config.Constants;
import org.lzh.proxy.management.MetricsRegistry;
import org.lzh.proxy.protocol.ProxyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.Attribute;

/**
 * 客户端到被代理应用的出站连接处理器（原 ClientDataHandler 的 app 通道部分）。
 */
public class AppConnectionHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(AppConnectionHandler.class);

    private final Client client;
    private final ClientProxy proxy;
    private final MetricsRegistry metrics;

    public AppConnectionHandler(Client client, ClientProxy proxy, MetricsRegistry metrics) {
        this.client = client;
        this.proxy = proxy;
        this.metrics = metrics;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        Attribute<Long> serial = channel.attr(Constants.CHANNEL_SERIAL);
        Channel registerChannel = proxy.registerChannel();
        if (registerChannel != null && registerChannel.isOpen()) {
            registerChannel.writeAndFlush(ProxyMessage.disconnect(serial.get()));
        }
        client.registry().clientChannel().remove(serial.get());
        metrics.tunnelClosed();
        log.debug("serial: {},代理关闭", serial.get());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
        Channel channel = ctx.channel();
        Channel registerChannel = proxy.registerChannel();
        if (registerChannel != null && registerChannel.isOpen()) {
            Attribute<Long> serial = channel.attr(Constants.CHANNEL_SERIAL);
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            metrics.bytesC2s(bytes.length);
            registerChannel.writeAndFlush(ProxyMessage.transfer(serial.get(), bytes));
        }
    }
}
