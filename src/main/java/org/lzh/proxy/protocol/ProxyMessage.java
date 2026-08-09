package org.lzh.proxy.protocol;

/**
 * 客户端/服务端交换协议消息。
 *
 * <p>不可变记录。线格式不变：{@code [4字节长度(9+data.len)][1字节type][8字节serial][data]}。</p>
 *
 * @param type   消息类型
 * @param serial 通道序列号
 * @param data   载荷，可为 null
 */
public record ProxyMessage(MessageType type, long serial, byte[] data) {

    public static ProxyMessage connect(long serial) {
        return new ProxyMessage(MessageType.CONNECT, serial, null);
    }

    public static ProxyMessage disconnect(long serial) {
        return new ProxyMessage(MessageType.DISCONNECT, serial, null);
    }

    public static ProxyMessage transfer(long serial, byte[] data) {
        return new ProxyMessage(MessageType.TRANSFER, serial, data);
    }

    public static ProxyMessage register(byte[] data) {
        return new ProxyMessage(MessageType.REGISTER, 0L, data);
    }

    public static ProxyMessage ping() {
        return new ProxyMessage(MessageType.HEARTBEAT_PING, -1L, null);
    }

    public static ProxyMessage pong() {
        return new ProxyMessage(MessageType.HEARTBEAT_PONG, -1L, null);
    }
}
