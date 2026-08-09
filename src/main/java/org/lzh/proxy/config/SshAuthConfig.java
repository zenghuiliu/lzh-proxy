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
 * SSH 认证方式（密封类型，二选一）。
 */
public sealed interface SshAuthConfig permits SshAuthConfig.PasswordAuth, SshAuthConfig.KeyAuth {

    /**
     * 密码认证。password 为经 SecretsResolver 解析后的最终明文（可来自 YAML/环境变量/CLI）。
     */
    record PasswordAuth(String password) implements SshAuthConfig {
    }

    /**
     * 私钥认证。passphrase 为密钥口令（可空）。
     *
     * @param privateKeyPath 私钥文件路径（如 ~/.ssh/id_rsa）
     * @param passphrase     密钥口令，可能为 null
     */
    record KeyAuth(String privateKeyPath, String passphrase) implements SshAuthConfig {
    }
}
