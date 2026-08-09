package org.lzh.proxy.tunnel.ssh;

/**
 * SSH 会话状态机状态。
 */
public enum SshState {
    /** 初始态。 */
    DISCONNECTED,
    /** 正在连接/认证。 */
    CONNECTING,
    /** 已连接，转发可正常工作。 */
    CONNECTED,
    /** 断开后进入退避等待重连。 */
    BACKOFF,
    /** 终态：主动停止或致命错误（认证失败/主机密钥不匹配）。 */
    STOPPED
}
