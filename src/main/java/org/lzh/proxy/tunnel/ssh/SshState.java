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

/**
 * SSH 会话状态机状态。
 */
public enum SshState {
    /** 初始态。 */
    DISCONNECTED,
    /** 正在连接/认证。 */
    CONNECTING,
    /** 已连接，转发可正常工作。 */
    CONNECTED,
    /** 断开后进入退避等待重连。 */
    BACKOFF,
    /** 终态：主动停止或致命错误（认证失败/主机密钥不匹配）。 */
    STOPPED
}
