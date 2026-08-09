/*
 * Copyright 2023-2026 lzh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lzh.proxy.protocol;

import org.lzh.proxy.config.Constants;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * 代理消息解码器。
 *
 * <p>基于 {@link LengthFieldBasedFrameDecoder}：INITIAL_BYTES_TO_STRIP=4 剥掉长度前缀，
 * 剩余载荷为 type(1) + serial(8) + data。最小帧校验杜绝畸形帧导致的
 * NegativeArraySizeException，超长帧由 maxFrameLength 拦截，未知类型按协议违规拒绝。</p>
 */
public class ProxyMessageDecoder extends LengthFieldBasedFrameDecoder {

    public ProxyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
                               int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected ProxyMessage decode(ChannelHandlerContext ctx, ByteBuf in2) throws Exception {
        ByteBuf in = (ByteBuf) super.decode(ctx, in2);
        if (in == null) {
            return null;
        }
        if (in.readableBytes() < Constants.TYPE_SIZE + Constants.SERIAL_SIZE) {
            throw new DecoderException("proxy frame too small: " + in.readableBytes() + " bytes");
        }
        byte type = in.readByte();
        long serial = in.readLong();
        int dataLen = in.readableBytes();
        byte[] data = new byte[dataLen];
        in.readBytes(data);
        in.release();
        MessageType messageType = MessageType.fromCode(type)
                .orElseThrow(() -> new DecoderException("unknown message type: " + type));
        return new ProxyMessage(messageType, serial, data);
    }
}
