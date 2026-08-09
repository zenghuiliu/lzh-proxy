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
package org.lzh.proxy.client;

import org.lzh.proxy.config.ProxyBinding;

import io.netty.channel.Channel;

/**
 * 客户端单条代理绑定的运行时状态。
 *
 * <p>不可变配置（{@link ProxyBinding}）与运行时通道状态分离：
 * 通道归属本运行时对象，不污染配置模型。</p>
 */
public class ClientProxy {

    private final ProxyBinding binding;

    /** 该绑定对应的注册（控制）通道。 */
    private volatile Channel registerChannel;

    /** 最近一次到被代理应用的通道（仅用于避免重复连接）。 */
    private volatile Channel appChannel;

    public ClientProxy(ProxyBinding binding) {
        this.binding = binding;
    }

    public ProxyBinding binding() {
        return binding;
    }

    public Channel registerChannel() {
        return registerChannel;
    }

    public void registerChannel(Channel registerChannel) {
        this.registerChannel = registerChannel;
    }

    public Channel appChannel() {
        return appChannel;
    }

    public void appChannel(Channel appChannel) {
        this.appChannel = appChannel;
    }
}
