package org.lzh.proxy.client;

import java.util.concurrent.TimeUnit;

import org.lzh.proxy.Main;
import org.lzh.proxy.client.handler.ClientDataHandler;
import org.lzh.proxy.client.handler.ClientIdleDataHandler;
import org.lzh.proxy.config.ChannelChache;
import org.lzh.proxy.config.Constants;
import org.lzh.proxy.config.GlobalConfig;
import org.lzh.proxy.config.GlobalConfig.ProxyInfo;
import org.lzh.proxy.protocol.ProxyMessage;
import org.lzh.proxy.protocol.ProxyMessageDecoder;
import org.lzh.proxy.protocol.ProxyMessageEncoder;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.Attribute;
import io.netty.util.HashedWheelTimer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Client {
    public static Bootstrap bootstrap = new Bootstrap();
    public static HashedWheelTimer timer = new HashedWheelTimer();

    public void init(){
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(Main.bossgroup);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {

            }
        });
        for (ProxyInfo proxyInfo : GlobalConfig.getInstance().getProxyInfos()){
            // connect register
            registerConnect(proxyInfo);
        }
    }

    public static void registerConnect(ProxyInfo proxyInfo){
        if (proxyInfo.getRegister() == null || !proxyInfo.getRegister().isOpen()) {
            bootstrap.connect(GlobalConfig.getInstance().getRegisterIp().trim(), GlobalConfig.getInstance().getRegisterPort()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Channel registerChannel = future.channel();
                    proxyInfo.setRegister(registerChannel);
                    if (registerChannel != null && registerChannel.isOpen()) {
                        registerChannel.config().setOption(ChannelOption.SO_KEEPALIVE,true);
                        registerChannel.pipeline().addLast(new ProxyMessageDecoder(Constants.MAX_FRAME_LENGTH, Constants.LENGTH_FIELD_OFFSET, Constants.LENGTH_FIELD_LENGTH, Constants.LENGTH_ADJUSTMENT, Constants.INITIAL_BYTES_TO_STRIP));
                        registerChannel.pipeline().addLast(new ProxyMessageEncoder());
                        registerChannel.pipeline().addLast(new IdleStateHandler(0,0,60,TimeUnit.SECONDS));
                        registerChannel.pipeline().addLast(new ClientIdleDataHandler());
                        registerChannel.pipeline().addLast(new ClientDataHandler(proxyInfo,true));
                        String info = proxyInfo.getIp() + "," + proxyInfo.getPort() + "," + proxyInfo.getRemotePort() + "\r\n";
                        ProxyMessage proxyMessage = new ProxyMessage();
                        proxyMessage.setType(Constants.TYPE_REGISTER);
                        proxyMessage.setData(info.getBytes());
                        registerChannel.writeAndFlush(proxyMessage);
                        log.info("client connected register!");
                    } else {
                        timer.newTimeout(timeout -> {registerConnect(proxyInfo);},10, TimeUnit.SECONDS);
                        log.error("client connect register error!");
                    }
                }
            });
        }
    }

    public static void proxyConnect(ProxyInfo proxyInfo,Long serial){
        if (proxyInfo.getChannel() == null || !proxyInfo.getChannel().isOpen()) {
            ChannelFuture channelFuture = bootstrap.connect(proxyInfo.getIp().trim(), proxyInfo.getPort()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    Channel channel = future.channel();
                    Attribute<Long> serialAttr = channel.attr(Constants.CHANNEL_SERIAL);
                    serialAttr.set(serial);
                    Channel registerChannel = proxyInfo.getRegister();
                    if (channel != null && channel.isOpen()) {
                        channel.pipeline().addLast(new ClientDataHandler(proxyInfo,false));
                        if (registerChannel != null && registerChannel.isOpen()){
                            ProxyMessage proxyMessage = new ProxyMessage();
                            proxyMessage.setSerial(serial);
                            proxyMessage.setType(Constants.TYPE_CONNECT);
                            proxyMessage.setData(null);
                            registerChannel.writeAndFlush(proxyMessage);
                        }
                        log.debug("client connected proxy! serial：{}",serial);
                    } else {
                        ChannelChache.clientChannelMap.remove(serial);
                        if (registerChannel != null && registerChannel.isOpen()){
                            ProxyMessage proxyMessage = new ProxyMessage();
                            proxyMessage.setSerial(serial);
                            proxyMessage.setType(Constants.TYPE_DISCONNECT);
                            proxyMessage.setData(null);
                            registerChannel.writeAndFlush(proxyMessage);
                        }
                        log.error("client connect proxy error! serial：{}",serial);
                    }
                }
            });
            try {
                ChannelChache.clientChannelMap.put(serial,channelFuture.channel());
                channelFuture.sync();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
