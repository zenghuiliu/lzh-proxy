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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.sshd.client.SshClient;
import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.config.ServerEndpoint;
import org.lzh.proxy.config.SshEndpointConfig;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.management.MetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSH 会话管理器：为每条 {@code sshInfos} 配置维护一个 {@link SshSession} 状态机，
 * 统一管理共享 SshClient 的生命周期与转发路由。
 */
public class SshSessionManager implements Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(SshSessionManager.class);

    private final AppConfig config;
    private final ScheduledExecutorService scheduler;
    private final ReconnectPolicy policy;
    private final LocalForwarder forwarder;
    private final MetricsRegistry metrics;
    private final SshClient client;
    private final ConcurrentHashMap<String, SshSession> sessions = new ConcurrentHashMap<>();

    public SshSessionManager(AppConfig config, ScheduledExecutorService scheduler, MetricsRegistry metrics) {
        this.config = config;
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.policy = ReconnectPolicy.defaults();
        this.forwarder = new MinaLocalForwarder();
        this.client = SshClient.setUpDefaultClient();
        this.client.setServerKeyVerifier(new HostKeyVerifierFactory(config.security()).create());
    }

    @Override
    public void start() {
        client.start();
        for (SshEndpointConfig ssh : config.sshEndpoints()) {
            SshSession session = new SshSession(ssh, client, scheduler, policy, forwarder, metrics);
            sessions.put(ssh.id(), session);
            // 异步发起连接，避免阻塞服务端整体启动（register 监听可立即就绪）
            scheduler.execute(session::start);
        }
    }

    @Override
    public void stop() {
        for (SshSession session : sessions.values()) {
            session.stop();
        }
        sessions.clear();
        client.stop();
    }

    public void addForward(ServerEndpoint endpoint) {
        SshSession session = sessions.get(endpoint.sshId());
        if (session == null) {
            log.warn("ssh[{}] session not found，忽略转发[{}]", endpoint.sshId(), endpoint.port());
            return;
        }
        session.addForward(endpoint);
    }

    public void removeForward(ServerEndpoint endpoint) {
        SshSession session = sessions.get(endpoint.sshId());
        if (session != null) {
            session.removeForward(endpoint);
        }
    }

    /** 当前各 SSH 连接状态快照（供管理端点使用）。 */
    public ConcurrentHashMap<String, SshState> snapshotStates() {
        ConcurrentHashMap<String, SshState> snapshot = new ConcurrentHashMap<>();
        sessions.forEach((id, session) -> snapshot.put(id, session.state()));
        return snapshot;
    }
}
