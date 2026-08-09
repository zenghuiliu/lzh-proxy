package org.lzh.proxy.management;

/**
 * 指标快照。
 *
 * @param tunnelsOpened        累计建立隧道数
 * @param tunnelsClosed        累计关闭隧道数
 * @param activeTunnels        当前活动隧道数
 * @param bytesS2c             服务端->客户端累计字节
 * @param bytesC2s             客户端->服务端累计字节
 * @param controlReconnects    控制通道重连次数
 * @param sshReconnectAttempts SSH 重连尝试次数
 * @param sshReconnectFailures SSH 致命失败次数
 * @param registerRejects      被拒绝的注册请求数
 * @param uptimeSeconds        进程运行秒数
 */
public record Metrics(long tunnelsOpened, long tunnelsClosed, long activeTunnels,
                      long bytesS2c, long bytesC2s, long controlReconnects,
                      long sshReconnectAttempts, long sshReconnectFailures, long registerRejects,
                      long uptimeSeconds) {
}
