package org.lzh.proxy.client.handler;

import org.lzh.proxy.config.Constants;
import org.lzh.proxy.protocol.ProxyMessage;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public class ClientIdleDataHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state().equals(IdleState.ALL_IDLE)){
                ProxyMessage proxyMessage = new ProxyMessage();
                proxyMessage.setType(Constants.TYPE_HEART_BEET_PING);
                proxyMessage.setSerial(-1);
                ctx.writeAndFlush(proxyMessage);
            }
        }else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
