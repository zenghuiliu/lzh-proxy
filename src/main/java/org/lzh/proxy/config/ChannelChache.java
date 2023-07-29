package org.lzh.proxy.config;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelChache {
    public static volatile Map<Integer, Channel> proxyToReqMap = new ConcurrentHashMap<>();
    public static volatile Map<Integer, Channel> portToServerMap = new ConcurrentHashMap<>();
    public static volatile Map<Long, Channel> serverChannelMap = new ConcurrentHashMap<>();
    public static volatile Map<Long, Channel> clientChannelMap = new ConcurrentHashMap<>();
}
