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
 * SSH 失败分类：决定是否继续重连。
 */
public enum SshFailureKind {
    /** 瞬时错误（网络/服务器重启等），应退避重试。 */
    TRANSIENT,
    /** 认证失败（密码/密钥错误），重试无意义，进入终态。 */
    AUTH_FAILED,
    /** 主机密钥不匹配（MITM 或换钥），进入终态。 */
    HOST_KEY_MISMATCH
}
