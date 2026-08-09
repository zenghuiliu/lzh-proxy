package org.lzh.proxy.config;

/**
 * 客户端代理绑定：客户端本地应用与请求服务端开放的端口。
 *
 * @param appIp      被代理应用地址（客户端可达）
 * @param appPort    被代理应用端口
 * @param remotePort 请求服务端开放的外网端口（即服务端 serverInfos 中的 port）
 */
public record ProxyBinding(String appIp, int appPort, int remotePort) {
}
