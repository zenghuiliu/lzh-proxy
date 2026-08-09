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

/**
 * 一条 SSH 跳板连接配置。
 *
 * @param id                   唯一标识，被 serverInfos.sshId 引用
 * @param host                 SSH 服务器地址
 * @param port                 SSH 端口（默认 22）
 * @param username             SSH 用户名
 * @param auth                 认证方式（密码或私钥）
 * @param connectTimeoutMs     连接超时（毫秒）
 * @param keepAliveIntervalSec 保活间隔（秒）
 * @param keepAliveCountMax    保活失败判定次数
 */
public record SshEndpointConfig(String id, String host, int port, String username, SshAuthConfig auth,
                                int connectTimeoutMs, int keepAliveIntervalSec, int keepAliveCountMax) {
}
