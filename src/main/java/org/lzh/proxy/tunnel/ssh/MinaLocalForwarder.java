/*
 * Copyright 2023-2026 lzh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
