package org.lzh.proxy.client.handler;

import org.lzh.proxy.client.Client;
import org.lzh.proxy.config.ChannelChache;
import org.lzh.proxy.config.Constants;
import org.lzh.proxy.config.GlobalConfig;
import org.lzh.proxy.protocol.ProxyMessage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.Attribute;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class ClientDataHandler extends SimpleChannelInboundHandler<Object> {
    private GlobalConfig.ProxyInfo proxyInfo;
    private Boolean isRegister;
    public ClientDataHandler(GlobalConfig.ProxyInfo proxyInfo,Boolean isRegister){
        this.proxyInfo = proxyInfo;
        this.isRegister = isRegister;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (isRegister) {
            log.warn("服务端连接端口 {} 断连，重连中！", GlobalConfig.getInstance().getRegisterPort());
            Client.registerConnect(proxyInfo);
        }else {
            Attribute<Long> serial = channel.attr(Constants.CHANNEL_SERIAL);
            Channel registerChannel = proxyInfo.getRegister();
            if (registerChannel != null && registerChannel.isOpen()){
                ProxyMessage proxyMessage = new ProxyMessage();
                proxyMessage.setType(Constants.TYPE_DISCONNECT);
                proxyMessage.setSerial(serial.get());
                proxyMessage.setData(null);
                registerChannel.writeAndFlush(proxyMessage);
            }
            ChannelChache.clientChannelMap.remove(serial.get());
            log.debug("serial: {},代理关闭",serial.get());
        }
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel channel = ctx.channel();
        if (isRegister){
            if (msg instanceof ProxyMessage) {
                handlerRegisterData(ctx, (ProxyMessage)msg);
            }
        }else {
            if (msg instanceof ByteBuf) {
                Channel register = proxyInfo.getRegister();
                if (register != null && register.isOpen()) {
                    Attribute<Long> serial = channel.attr(Constants.CHANNEL_SERIAL);
                    ProxyMessage proxyMessage = new ProxyMessage();
                    proxyMessage.setType(Constants.TYPE_TRANSFER);
                    proxyMessage.setSerial(serial.get());
                    ByteBuf byteBuf = (ByteBuf)msg;
                    byte[] bytes = new byte[byteBuf.readableBytes()];
                    byteBuf.readBytes(bytes);
                    proxyMessage.setData(bytes);
                    register.writeAndFlush(proxyMessage);
                }
            }
        }
    }

    private void handlerRegisterData(ChannelHandlerContext ctx, ProxyMessage msg) {
        if (msg.getType() == Constants.TYPE_CONNECT){
            Client.proxyConnect(proxyInfo, msg.getSerial());
        }
        if (msg.getType() == Constants.TYPE_TRANSFER){
            Channel proxyChannel = ChannelChache.clientChannelMap.get(msg.getSerial());
            if (proxyChannel != null && proxyChannel.isOpen()) {
                proxyChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.getData()));
            }
        }
        if (msg.getType() == Constants.TYPE_DISCONNECT){
            Channel proxyChannel = ChannelChache.clientChannelMap.get(msg.getSerial());
            if (proxyChannel != null && proxyChannel.isOpen()){
                proxyChannel.close();
            }
        }
        if (msg.getType() == Constants.TYPE_HEART_BEET_PONG){
            log.debug("{} pong",ctx.channel().remoteAddress().toString());
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        super.channelWritabilityChanged(ctx);
    }
}
