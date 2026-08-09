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
    /**
     * 以下是消息交换的类型
     */
    byte TYPE_CONNECT = 0x01;

    byte TYPE_DISCONNECT = 0x02;

    byte TYPE_TRANSFER = 0x03;

    byte TYPE_REGISTER = 0x05;

    byte TYPE_HEART_BEET_PING = 0x06;
    byte TYPE_HEART_BEET_PONG = 0x07;
}
