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

import java.util.Map;

/**
 * 密钥解析器：将 YAML 中的直填值 / 环境变量名 / CLI 覆盖统一解析为最终密钥。
 *
 * <p>优先级：CLI 覆盖（--ssh.&lt;field&gt;.&lt;id&gt;=...）&gt; 环境变量（passwordEnv 指定的变量名）&gt; YAML 直填值。</p>
 */
public class SecretsResolver {

    private final Map<String, String> env;
    private final Map<String, String> cliOverrides;

    public SecretsResolver(Map<String, String> env, Map<String, String> cliOverrides) {
        this.env = env;
        this.cliOverrides = cliOverrides;
    }

    /**
     * 解析一条 SSH 认证密钥。
     *
     * @param sshId    SSH 配置 id
     * @param field    字段名（password / passphrase）
     * @param direct   YAML 直填值，可为 null
     * @param envVar   YAML 中指定的环境变量名，可为 null
     */
    public String resolveSecret(String sshId, String field, String direct, String envVar) {
        String cliKey = "ssh." + field + "." + sshId;
        String cli = cliOverrides.get(cliKey);
        if (cli != null) {
            return cli;
        }
        if (envVar != null && !envVar.isBlank()) {
            String value = env.get(envVar);
            if (value != null) {
                return value;
            }
        }
        return direct;
    }
}
