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
 * 客户端代理绑定：客户端本地应用与请求服务端开放的端口。
 *
 * @param appIp      被代理应用地址（客户端可达）
 * @param appPort    被代理应用端口
 * @param remotePort 请求服务端开放的外网端口（即服务端 serverInfos 中的 port）
 */
public record ProxyBinding(String appIp, int appPort, int remotePort) {
}
