package org.lzh.proxy.register.handler;

import org.lzh.proxy.config.Constants;
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
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * 服务端注册（控制）通道处理器：处理客户端注册、路由数据与控制消息。
 */
public class RegisterDataHandler extends SimpleChannelInboundHandler<ProxyMessage> {

    private static final Logger log = LoggerFactory.getLogger(RegisterDataHandler.class);

    private final TunnelRegistry registry;
    private final Server server;
    private final MetricsRegistry metrics;

    public RegisterDataHandler(TunnelRegistry registry, Server server, MetricsRegistry metrics) {
        this.registry = registry;
        this.server = server;
        this.metrics = metrics;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage msg) {
        Channel reqChannel = ctx.channel();
        if (msg.type() == Constants.TYPE_REGISTER) {
            handleRegister(reqChannel, msg);
        } else if (msg.type() == Constants.TYPE_DISCONNECT) {
            Channel sndChannel = registry.serverChannel().get(msg.serial());
            if (sndChannel != null && sndChannel.isOpen()) {
                sndChannel.close();
            }
        } else if (msg.type() == Constants.TYPE_TRANSFER) {
            Channel sndChannel = registry.serverChannel().get(msg.serial());
            if (sndChannel != null && sndChannel.isOpen()) {
                if (msg.data() != null) {
                    metrics.bytesC2s(msg.data().length);
                }
                sndChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.data()));
            }
        } else if (msg.type() == Constants.TYPE_CONNECT) {
            Channel sndChannel = registry.serverChannel().get(msg.serial());
            if (sndChannel != null && sndChannel.isOpen()) {
                sndChannel.config().setOption(ChannelOption.AUTO_READ, true);
            }
        } else if (msg.type() == Constants.TYPE_HEART_BEET_PING) {
            log.debug("{} ping", reqChannel.remoteAddress());
            reqChannel.writeAndFlush(ProxyMessage.pong());
        }
    }

    private void handleRegister(Channel reqChannel, ProxyMessage msg) {
        String[] info = new String(msg.data()).split(",");
        Integer reqPort = null;
        try {
            reqPort = Integer.valueOf(info[2].trim());
        } catch (Exception e) {
            log.info("注册信息不合法！msg：{}", (Object) info, e);
            metrics.registerReject();
        }
        if (reqPort == null || info.length < 3) {
            return;
        }
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
