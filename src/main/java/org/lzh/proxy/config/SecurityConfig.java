package org.lzh.proxy.config;

import java.nio.file.Path;

/**
 * 安全相关配置。
 *
 * @param hostKeyPolicy SSH 主机密钥校验策略
 * @param knownHostsPath known_hosts 文件路径（TOFU 策略时使用）
 */
public record SecurityConfig(HostKeyPolicy hostKeyPolicy, Path knownHostsPath) {

    /**
     * SSH 主机密钥校验策略。
     */
    public enum HostKeyPolicy {
        /** 首次连接接受并记录，之后必须匹配（默认）。 */
        TOFU_KNOWN_HOSTS,
        /** 严格校验，未知主机一律拒绝。 */
        STRICT,
        /** 不校验（逃生舱，启动时显著告警）。 */
        ACCEPT_ALL
    }
}
