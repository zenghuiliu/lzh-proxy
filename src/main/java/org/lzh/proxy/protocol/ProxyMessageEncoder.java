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
