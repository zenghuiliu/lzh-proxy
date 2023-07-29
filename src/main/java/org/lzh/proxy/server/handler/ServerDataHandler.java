package org.lzh.proxy.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

import org.lzh.proxy.Main;
import org.lzh.proxy.config.ChannelChache;
import org.lzh.proxy.config.Constants;
import org.lzh.proxy.protocol.ProxyMessage;

@Slf4j
public class ServerDataHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        channel.config().setOption(ChannelOption.AUTO_READ,false);

        Attribute<Long> serial = channel.attr(Constants.CHANNEL_SERIAL);
        serial.set(Main.serverChannelSerial.getAndIncrement());
        ChannelChache.serverChannelMap.put(serial.get(),channel);
        log.info("channel is active :{}",channel);
        sendMsg(channel,Constants.TYPE_CONNECT,null);
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        Channel reqChannel = ctx.channel();
        sendMsg(reqChannel,Constants.TYPE_TRANSFER,msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        sendMsg(channel,Constants.TYPE_DISCONNECT,null);
        if (channel != null && channel.isOpen()){
            channel.close();
        }
        // 移除引用
        Attribute<Long> serial = channel.attr(Constants.CHANNEL_SERIAL);
        ChannelChache.serverChannelMap.remove(serial.get());
        log.debug("channel is closed:{}",channel);
        super.channelInactive(ctx);
    }


    private void sendMsg(Channel channel,Byte type,ByteBuf msg) {
        InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
        int port = socketAddress.getPort();
        Attribute<Long> serial = channel.attr(Constants.CHANNEL_SERIAL);
        Channel proxyChannel = ChannelChache.proxyToReqMap.get(port);
        if(proxyChannel == null){
            return;
        }
        if (proxyChannel.isOpen()){
            ProxyMessage proxyMessage = new ProxyMessage();
            proxyMessage.setType(type);
            proxyMessage.setSerial(serial.get());
            if (msg != null) {
                byte[] bytes = new byte[msg.readableBytes()];
                msg.readBytes(bytes);
                proxyMessage.setData(bytes);
            }
            proxyChannel.writeAndFlush(proxyMessage);
        }
    }
}
