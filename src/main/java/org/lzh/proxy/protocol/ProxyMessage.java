package org.lzh.proxy.protocol;

import org.lzh.proxy.config.Constants;

/**
 * 客户端/服务端交换协议消息。
 *
 * <p>不可变记录。线格式不变：{@code [4字节长度(9+data.len)][1字节type][8字节serial][data]}。</p>
 *
 * @param type   消息类型，见 {@link Constants}
 * @param serial 通道序列号
 * @param data   载荷，可为 null
 */
public record ProxyMessage(byte type, long serial, byte[] data) {

    public static ProxyMessage connect(long serial) {
        return new ProxyMessage(Constants.TYPE_CONNECT, serial, null);
    }

    public static ProxyMessage disconnect(long serial) {
        return new ProxyMessage(Constants.TYPE_DISCONNECT, serial, null);
    }

    public static ProxyMessage transfer(long serial, byte[] data) {
        return new ProxyMessage(Constants.TYPE_TRANSFER, serial, data);
    }

    public static ProxyMessage register(byte[] data) {
        return new ProxyMessage(Constants.TYPE_REGISTER, 0L, data);
    }

    public static ProxyMessage ping() {
        return new ProxyMessage(Constants.TYPE_HEART_BEET_PING, -1L, null);
    }

    public static ProxyMessage pong() {
        return new ProxyMessage(Constants.TYPE_HEART_BEET_PONG, -1L, null);
    }
}
