package org.lzh.proxy.config;

/**
 * SSH 认证方式（密封类型，二选一）。
 */
public sealed interface SshAuthConfig permits SshAuthConfig.PasswordAuth, SshAuthConfig.KeyAuth {

    /**
     * 密码认证。password 为经 SecretsResolver 解析后的最终明文（可来自 YAML/环境变量/CLI）。
     */
    record PasswordAuth(String password) implements SshAuthConfig {
    }

    /**
     * 私钥认证。passphrase 为密钥口令（可空）。
     *
     * @param privateKeyPath 私钥文件路径（如 ~/.ssh/id_rsa）
     * @param passphrase     密钥口令，可能为 null
     */
    record KeyAuth(String privateKeyPath, String passphrase) implements SshAuthConfig {
    }
}
