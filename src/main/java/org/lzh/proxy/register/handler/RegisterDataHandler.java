package org.lzh.proxy.register.handler;

import org.lzh.proxy.Main;
import org.lzh.proxy.config.ChannelChache;
import org.lzh.proxy.config.Constants;
import org.lzh.proxy.config.GlobalConfig;
import org.lzh.proxy.protocol.ProxyMessage;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RegisterDataHandler extends SimpleChannelInboundHandler<ProxyMessage> {
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // TODO Auto-generated method stub
        super.channelInactive(ctx);
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage msg) throws Exception {
        Channel reqChannel = ctx.channel();
        if (msg.getType() == Constants.TYPE_REGISTER) {
            String[] info = (new String(msg.getData())).split(",");
            Integer reqPort = null;
            try {
                reqPort = Integer.valueOf(info[2].trim());
            } catch (Exception e) {
                log.info("注册信息不合法！msg：{}", info, e);
            }
            if (reqPort != null){
                if (info.length >= 3){
                    GlobalConfig.ProxyInfo proxyInfo = null;
                    for (GlobalConfig.ProxyInfo proxyInfo1 : GlobalConfig.getInstance().getProxyInfos()){
                        if (proxyInfo1.getRemotePort().equals(reqPort)){
                            proxyInfo = proxyInfo1;
                        }
                    }
                    if (proxyInfo == null) {
                        proxyInfo = new GlobalConfig.ProxyInfo();
                        GlobalConfig.getInstance().getProxyInfos().add(proxyInfo);
                    }
                    proxyInfo.setIp(info[0]);
                    proxyInfo.setPort(Integer.valueOf(info[1].trim()));
                    proxyInfo.setRemotePort(reqPort);
                    Channel oldChannel = ChannelChache.proxyToReqMap.get(reqPort);
                    if(oldChannel != null && oldChannel.isOpen()){
                        log.info("请求端口已打开：{}，正在关闭……", reqPort);
                        oldChannel.close().sync();
                        ChannelChache.proxyToReqMap.remove(reqPort);
                    }
                    ChannelChache.proxyToReqMap.put(reqPort,reqChannel);
                    Channel serverChannel = ChannelChache.portToServerMap.get(reqPort);
                    if(serverChannel == null || !serverChannel.isOpen()){
                        GlobalConfig.ServerInfo serverInfo = new GlobalConfig.ServerInfo();
                        serverInfo.setIp("0.0.0.0");
                        serverInfo.setPort(reqPort);
                        Main.server.bindPort(serverInfo);
                    }
                    log.info("被代理端口已连接到 {}", reqPort);
                }
            }
        }else if (msg.getType() == Constants.TYPE_DISCONNECT){
            Channel sndChannel = ChannelChache.serverChannelMap.get(msg.getSerial());
            if (sndChannel != null && sndChannel.isOpen()){
                sndChannel.close();
            }
        } else if (msg.getType() == Constants.TYPE_TRANSFER){
            Channel sndChannel = ChannelChache.serverChannelMap.get(msg.getSerial());
            if (sndChannel != null && sndChannel.isOpen()){
                sndChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.getData()));
            }
        }else if (msg.getType() == Constants.TYPE_CONNECT){
            Channel sndChannel = ChannelChache.serverChannelMap.get(msg.getSerial());
            if (sndChannel != null && sndChannel.isOpen()){
                sndChannel.config().setOption(ChannelOption.AUTO_READ,true);
            }
        }else if (msg.getType() == Constants.TYPE_HEART_BEET_PING){
            log.debug("{} ping",reqChannel.remoteAddress().toString());
            ProxyMessage proxyMessage = new ProxyMessage();
            proxyMessage.setType(Constants.TYPE_HEART_BEET_PONG);
            proxyMessage.setSerial(-1);
            reqChannel.writeAndFlush(proxyMessage);
        }
    }
}
