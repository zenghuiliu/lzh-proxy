package org.lzh.proxy.tunnel.ssh;

import java.io.IOException;

import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.lzh.proxy.config.ServerEndpoint;

/**
 * 基于 Apache MINA SSHD 的本地端口转发实现。
 */
public class MinaLocalForwarder implements LocalForwarder {

    @Override
    public SshdSocketAddress start(ClientSession session, ServerEndpoint endpoint) throws IOException {
        String bindHost = (endpoint.ip() == null || endpoint.ip().isBlank()) ? "0.0.0.0" : endpoint.ip();
        return session.startLocalPortForwarding(
                new SshdSocketAddress(bindHost, endpoint.port()),
                new SshdSocketAddress(endpoint.forwardIp(), endpoint.forwardPort()));
    }
}
