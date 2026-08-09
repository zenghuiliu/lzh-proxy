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
package org.lzh.proxy.management;

/**
 * 指标快照。
 *
 * @param tunnelsOpened        累计建立隧道数
 * @param tunnelsClosed        累计关闭隧道数
 * @param activeTunnels        当前活动隧道数
 * @param bytesS2c             服务端->客户端累计字节
 * @param bytesC2s             客户端->服务端累计字节
 * @param controlReconnects    控制通道重连次数
 * @param sshReconnectAttempts SSH 重连尝试次数
 * @param sshReconnectFailures SSH 致命失败次数
 * @param registerRejects      被拒绝的注册请求数
 * @param uptimeSeconds        进程运行秒数
 */
public record Metrics(long tunnelsOpened, long tunnelsClosed, long activeTunnels,
                      long bytesS2c, long bytesC2s, long controlReconnects,
                      long sshReconnectAttempts, long sshReconnectFailures, long registerRejects,
                      long uptimeSeconds) {
}
