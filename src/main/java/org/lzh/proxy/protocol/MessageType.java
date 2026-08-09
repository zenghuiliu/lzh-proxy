package org.lzh.proxy.protocol;

import java.util.Optional;

/**
 * 协议消息类型（线格式字节见 {@link #code()}）。
 */
public enum MessageType {
    CONNECT(0x01),
    DISCONNECT(0x02),
    TRANSFER(0x03),
    REGISTER(0x05),
    HEARTBEAT_PING(0x06),
    HEARTBEAT_PONG(0x07);

    private final byte code;

    MessageType(int code) {
        this.code = (byte) code;
    }

    /** 线格式字节值。 */
    public byte code() {
        return code;
    }

    /** 由线格式字节反查类型；未知字节返回空。 */
    public static Optional<MessageType> fromCode(byte b) {
        for (MessageType type : values()) {
            if (type.code == b) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
