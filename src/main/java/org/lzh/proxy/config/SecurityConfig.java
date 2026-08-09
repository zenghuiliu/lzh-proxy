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

import java.nio.file.Path;

/**
 * 安全相关配置。
 *
 * @param hostKeyPolicy SSH 主机密钥校验策略
 * @param knownHostsPath known_hosts 文件路径（TOFU 策略时使用）
 */
public record SecurityConfig(HostKeyPolicy hostKeyPolicy, Path knownHostsPath) {

    /**
     * SSH 主机密钥校验策略。
     */
    public enum HostKeyPolicy {
        /** 首次连接接受并记录，之后必须匹配（默认）。 */
        TOFU_KNOWN_HOSTS,
        /** 严格校验，未知主机一律拒绝。 */
        STRICT,
        /** 不校验（逃生舱，启动时显著告警）。 */
        ACCEPT_ALL
    }
}
