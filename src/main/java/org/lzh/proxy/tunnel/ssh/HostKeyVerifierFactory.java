package org.lzh.proxy.tunnel.ssh;

import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.lzh.proxy.config.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按配置的 {@link SecurityConfig.HostKeyPolicy} 创建服务端主机密钥校验器。
 *
 * <ul>
 *   <li>TOFU_KNOWN_HOSTS（默认）：unknown host 首次连接接受并记录，之后必须匹配</li>
 *   <li>STRICT：仅接受 known_hosts 中记录的主机</li>
 *   <li>ACCEPT_ALL：不校验（逃生舱，启动时显著告警）</li>
 * </ul>
 */
public class HostKeyVerifierFactory {

    private static final Logger log = LoggerFactory.getLogger(HostKeyVerifierFactory.class);

    private final SecurityConfig config;

    public HostKeyVerifierFactory(SecurityConfig config) {
        this.config = config;
    }

    public ServerKeyVerifier create() {
        return switch (config.hostKeyPolicy()) {
            case ACCEPT_ALL -> {
                log.warn("hostKeyPolicy=ACCEPT_ALL：主机密钥校验已关闭，存在中间人攻击（MITM）风险！");
                yield AcceptAllServerKeyVerifier.INSTANCE;
            }
            case STRICT -> new KnownHostsServerKeyVerifier(RejectAllServerKeyVerifier.INSTANCE, config.knownHostsPath());
            case TOFU_KNOWN_HOSTS -> new KnownHostsServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE, config.knownHostsPath());
        };
    }
}
