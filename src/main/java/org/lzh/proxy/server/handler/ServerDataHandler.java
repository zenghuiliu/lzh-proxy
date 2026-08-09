package org.lzh.proxy.server.handler;

import org.lzh.proxy.config.Constants;
import org.lzh.proxy.core.SerialGenerator;
import org.lzh.proxy.forward.TunnelRegistry;
import org.lzh.proxy.management.MetricsRegistry;
import org.lzh.proxy.protocol.ProxyMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.Attribute;

import java.net.InetSocketAddress;

/**
 * 服务端入站用户连接处理器：分配序列号、暂停读、向控制通道发送 CONNECT/TRANSFER/DISCONNECT。
 */
public class ServerDataHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(ServerDataHandler.class);

    private final TunnelRegistry registry;
    private final SerialGenerator serial;
    private final MetricsRegistry metrics;

    public ServerDataHandler(TunnelRegistry registry, SerialGenerator serial, MetricsRegistry metrics) {
        this.registry = registry;
        this.serial = serial;
        this.metrics = metrics;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        channel.config().setOption(ChannelOption.AUTO_READ, false);

        Attribute<Long> serialAttr = channel.attr(Constants.CHANNEL_SERIAL);
        serialAttr.set(serial.next());
        registry.serverChannel().put(serialAttr.get(), channel);
        metrics.tunnelOpened();
        log.info("channel is active :{}", channel);
        sendMsg(channel, Constants.TYPE_CONNECT, null);
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel reqChannel = ctx.channel();
        metrics.bytesS2c(msg.readableBytes());
        sendMsg(reqChannel, Constants.TYPE_TRANSFER, msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        sendMsg(channel, Constants.TYPE_DISCONNECT, null);
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        Attribute<Long> serialAttr = channel.attr(Constants.CHANNEL_SERIAL);
        registry.serverChannel().remove(serialAttr.get());
        metrics.tunnelClosed();
        log.debug("channel is closed:{}", channel);
        super.channelInactive(ctx);
    }

    private void sendMsg(Channel channel, byte type, ByteBuf msg) {
        InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
        int port = socketAddress.getPort();
        Attribute<Long> serialAttr = channel.attr(Constants.CHANNEL_SERIAL);
        Channel proxyChannel = registry.proxyToReq().get(port);
        if (proxyChannel == null || !proxyChannel.isOpen()) {
            return;
        }
        byte[] bytes = null;
        if (msg != null) {
            bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
        }
        proxyChannel.writeAndFlush(new ProxyMessage(type, serialAttr.get(), bytes));
    }
}
