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
package org.lzh.proxy.config;

import java.util.List;

/**
 * 应用不可变配置根模型（替代原 GlobalConfig 单例）。
 *
 * <p>配置与运行时状态彻底分离：本模型不含任何 Channel / 连接状态。
 * 运行时通道归属 TunnelRegistry / ControlChannel 等组件。</p>
 *
 * @param role            运行角色
 * @param register        注册（控制）通道监听配置
 * @param serverEndpoints 服务端暴露端口列表
 * @param proxyBindings   客户端代理绑定列表
 * @param sshEndpoints    SSH 跳板连接列表
 * @param security        安全配置
 * @param management      管理端点配置
 */
public record AppConfig(Role role, RegisterConfig register, List<ServerEndpoint> serverEndpoints,
                        List<ProxyBinding> proxyBindings, List<SshEndpointConfig> sshEndpoints,
                        SecurityConfig security, ManagementConfig management) {

    /** 是否为服务端角色。 */
    public boolean isServer() {
        return role == Role.SERVER;
    }
}
