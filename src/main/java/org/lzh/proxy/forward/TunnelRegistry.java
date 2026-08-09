package org.lzh.proxy.forward;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.Channel;

/**
 * 隧道通道注册表（替代原静态 ChannelChache）。
 *
 * <p>由组合根创建单例并注入各处，消除全局可变静态状态。
 * Phase 1 先承接原 ChannelChache 的四张映射；后续阶段演化为带 Tunnel 对象与
 * 统一清理能力的完整注册表。</p>
 */
public class TunnelRegistry {

    /** 本地代理端口 -> 注册（控制）通道。 */
    private final ConcurrentHashMap<Integer, Channel> proxyToReqMap = new ConcurrentHashMap<>();

    /** 本地代理端口 -> 已绑定的服务端监听 Channel。 */
    private final ConcurrentHashMap<Integer, Channel> portToServerMap = new ConcurrentHashMap<>();

    /** 序列号 -> 服务端入站用户连接通道。 */
    private final ConcurrentHashMap<Long, Channel> serverChannelMap = new ConcurrentHashMap<>();

    /** 序列号 -> 客户端到被代理应用的出站连接通道。 */
    private final ConcurrentHashMap<Long, Channel> clientChannelMap = new ConcurrentHashMap<>();

    public ConcurrentHashMap<Integer, Channel> proxyToReq() {
        return proxyToReqMap;
    }

    public ConcurrentHashMap<Integer, Channel> portToServer() {
        return portToServerMap;
    }

    public ConcurrentHashMap<Long, Channel> serverChannel() {
        return serverChannelMap;
    }

    public ConcurrentHashMap<Long, Channel> clientChannel() {
        return clientChannelMap;
    }

    /** 供调试/指标使用的只读快照。 */
    public Map<String, Integer> snapshotSizes() {
        return Map.of(
                "proxyToReq", proxyToReqMap.size(),
                "portToServer", portToServerMap.size(),
                "serverChannel", serverChannelMap.size(),
                "clientChannel", clientChannelMap.size());
    }
}
