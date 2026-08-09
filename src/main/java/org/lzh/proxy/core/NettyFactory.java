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
package org.lzh.proxy.core;

import java.util.concurrent.TimeUnit;

import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.util.NamedThreadFactory;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * 拥有并管理全局共享的 Netty 事件循环组。
 *
 * <p>替代原 Main 中的静态 bossgroup/workgroup：由组合根持有并注入各处，
 * 统一在停机时优雅关闭。</p>
 */
public class NettyFactory implements Lifecycle {

    private final NioEventLoopGroup bossGroup;
    private final NioEventLoopGroup workGroup;

    public NettyFactory() {
        this.bossGroup = new NioEventLoopGroup(0, new NamedThreadFactory("lzh-proxy-boss"));
        this.workGroup = new NioEventLoopGroup(0, new NamedThreadFactory("lzh-proxy-worker"));
    }

    /** 接受连接的事件循环组。 */
    public EventLoopGroup boss() {
        return bossGroup;
    }

    /** 处理已建立连接读写的事件循环组。 */
    public EventLoopGroup worker() {
        return workGroup;
    }

    @Override
    public void start() {
        // 事件循环组在构造时即创建，此处无额外动作
    }

    @Override
    public void stop() {
        bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        workGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
    }
}
