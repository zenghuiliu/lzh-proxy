package org.lzh.proxy.config;

/**
 * 一条 SSH 跳板连接配置。
 *
 * @param id                   唯一标识，被 serverInfos.sshId 引用
 * @param host                 SSH 服务器地址
 * @param port                 SSH 端口（默认 22）
 * @param username             SSH 用户名
 * @param auth                 认证方式（密码或私钥）
 * @param connectTimeoutMs     连接超时（毫秒）
 * @param keepAliveIntervalSec 保活间隔（秒）
 * @param keepAliveCountMax    保活失败判定次数
 */
public record SshEndpointConfig(String id, String host, int port, String username, SshAuthConfig auth,
                                int connectTimeoutMs, int keepAliveIntervalSec, int keepAliveCountMax) {
}
