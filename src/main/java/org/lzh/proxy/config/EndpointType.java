package org.lzh.proxy.config;

/**
 * 服务端暴露端口的转发类型。
 */
public enum EndpointType {
    /** 走 Netty 代理链路（默认）。 */
    TCP,
    /** 走 SSH 隧道转发。 */
    SSH
}
