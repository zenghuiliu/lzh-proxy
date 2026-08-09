package org.lzh.proxy.client.handler;

import org.lzh.proxy.protocol.ProxyMessage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * 注册通道空闲处理器：超过空闲时间发送心跳 PING。
 */
public class ClientIdleDataHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent event && event.state().equals(IdleState.ALL_IDLE)) {
            ctx.writeAndFlush(ProxyMessage.ping());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
