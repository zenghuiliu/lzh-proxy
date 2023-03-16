package org.lzh.proxy.protocol;

import org.lzh.proxy.config.Constants;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ProxyMessageEncoder extends MessageToByteEncoder<ProxyMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ProxyMessage msg, ByteBuf out) throws Exception {
        int bodyLength = Constants.TYPE_SIZE + Constants.SERIAL_SIZE;

        if (msg.getData() != null) {
            bodyLength += msg.getData().length;
        }

        out.writeInt(bodyLength);

        out.writeByte(msg.getType());
        out.writeLong(msg.getSerial());

        if (msg.getData() != null) {
            out.writeBytes(msg.getData());
        }
    }
}