package org.lzh.proxy.protocol;

import org.lzh.proxy.config.Constants;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 代理消息编码器：输出与旧版本逐字节一致。
 */
public class ProxyMessageEncoder extends MessageToByteEncoder<ProxyMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ProxyMessage msg, ByteBuf out) throws Exception {
        int bodyLength = Constants.TYPE_SIZE + Constants.SERIAL_SIZE;

        if (msg.data() != null) {
            bodyLength += msg.data().length;
        }

        out.writeInt(bodyLength);

        out.writeByte(msg.type().code());
        out.writeLong(msg.serial());

        if (msg.data() != null) {
            out.writeBytes(msg.data());
        }
    }
}
