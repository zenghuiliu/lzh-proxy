package org.lzh.proxy.tunnel.ssh;

/**
 * SSH 失败分类：决定是否继续重连。
 */
public enum SshFailureKind {
    /** 瞬时错误（网络/服务器重启等），应退避重试。 */
    TRANSIENT,
    /** 认证失败（密码/密钥错误），重试无意义，进入终态。 */
    AUTH_FAILED,
    /** 主机密钥不匹配（MITM 或换钥），进入终态。 */
    HOST_KEY_MISMATCH
}
