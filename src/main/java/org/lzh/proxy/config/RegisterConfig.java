package org.lzh.proxy.config;

/**
 * 注册（控制）通道的服务端监听地址。
 *
 * @param ip   服务端监听 IP
 * @param port 服务端监听端口
 */
public record RegisterConfig(String ip, int port) {
}
