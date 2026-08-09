package org.lzh.proxy.tunnel.ssh;

import java.io.IOException;

import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.lzh.proxy.config.ServerEndpoint;

/**
 * 本地端口转发抽象，隔离 MINA SSHD 转发 API（便于测试替换）。
 */
public interface LocalForwarder {

    /**
     * 在会话上建立一条本地端口转发。
     *
     * @return 实际绑定地址（用于后续解除转发）
     */
    SshdSocketAddress start(ClientSession session, ServerEndpoint endpoint) throws IOException;
}
