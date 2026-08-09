package org.lzh.proxy.config;

/**
 * 服务端暴露的单个端口配置。
 *
 * @param type        转发类型：TCP 或 SSH
 * @param ip          服务端绑定 IP（TCP 类型时可为 null，表示绑定所有网卡）
 * @param port        服务端暴露端口
 * @param sshId       引用 sshInfos 中配置的 SSH 连接 id（SSH 类型必填）
 * @param forwardIp   转发目标 IP（SSH 类型必填）
 * @param forwardPort 转发目标端口（SSH 类型必填）
 */
public record ServerEndpoint(EndpointType type, String ip, int port, String sshId, String forwardIp,
                             Integer forwardPort) {
}
