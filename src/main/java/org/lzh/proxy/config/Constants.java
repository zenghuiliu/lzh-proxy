package org.lzh.proxy.config;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

public interface Constants {
    // 进入系统请求的channel序号
    public static final AttributeKey<Long> CHANNEL_SERIAL = AttributeKey.newInstance("channel_serial");

    /**
     * 以下是解码器配置
     */
    public static final int MAX_FRAME_LENGTH = 1024 * 1024;

    public static final int LENGTH_FIELD_OFFSET = 0;

    public static final int LENGTH_FIELD_LENGTH = 4;

    public static final int INITIAL_BYTES_TO_STRIP = 0;

    public static final int LENGTH_ADJUSTMENT = 0;

    // 消息头大小
    public static final byte HEADER_SIZE = 4;

    // 消息类型占用字节数
    public static final int TYPE_SIZE = 1;

    // 进入系统的请求序号占用字节数
    public static final int SERIAL_SIZE = 8;
    /**
     * 以下是消息交换的类型
     */
    public static final byte TYPE_CONNECT = 0x01;

    public static final byte TYPE_DISCONNECT = 0x02;

    public static final byte TYPE_TRANSFER = 0x03;

    public static final byte TYPE_REGISTER = 0x05;

    public static final byte TYPE_HEART_BEET_PING = 0x06;
    public static final byte TYPE_HEART_BEET_PONG = 0x07;
}
