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
package org.lzh.proxy.config;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * 协议与通道常量。
 */
public interface Constants {
    // 进入系统请求的channel序号
    AttributeKey<Long> CHANNEL_SERIAL = AttributeKey.newInstance("channel_serial");

    /**
     * 以下是解码器配置
     */
    int MAX_FRAME_LENGTH = 1024 * 1024;

    int LENGTH_FIELD_OFFSET = 0;

    int LENGTH_FIELD_LENGTH = 4;

    // 剥掉长度前缀，解出的帧直接为 type+serial+data
    int INITIAL_BYTES_TO_STRIP = 4;

    int LENGTH_ADJUSTMENT = 0;

    // 消息类型占用字节数
    int TYPE_SIZE = 1;

    // 进入系统的请求序号占用字节数
    int SERIAL_SIZE = 8;
}
