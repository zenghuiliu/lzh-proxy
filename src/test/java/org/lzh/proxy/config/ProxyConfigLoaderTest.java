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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 配置加载器与校验规则单元测试。
 */
class ProxyConfigLoaderTest {

    @Test
    void loadsClientProfile() {
        AppConfig config = ProxyConfigLoader.load("application", Map.of(), Map.of());
        assertThat(config.role()).isEqualTo(Role.CLIENT);
        assertThat(config.register()).isEqualTo(new RegisterConfig("127.0.0.1", 7000));
        assertThat(config.proxyBindings()).containsExactly(new ProxyBinding("127.0.0.1", 7002, 7001));
        assertThat(config.sshEndpoints()).isEmpty();
        assertThat(config.serverEndpoints()).isEmpty();
    }

    @Test
    void loadsServerProfileWithSshEndpoint() {
        AppConfig config = ProxyConfigLoader.load("server", Map.of(), Map.of());
        assertThat(config.role()).isEqualTo(Role.SERVER);
        assertThat(config.serverEndpoints()).hasSize(1);
        ServerEndpoint endpoint = config.serverEndpoints().get(0);
        assertThat(endpoint.type()).isEqualTo(EndpointType.SSH);
        assertThat(endpoint.port()).isEqualTo(8848);
        assertThat(endpoint.sshId()).isEqualTo("test");
        assertThat(endpoint.forwardIp()).isEqualTo("127.0.0.1");
        assertThat(endpoint.forwardPort()).isEqualTo(8848);
        assertThat(config.sshEndpoints()).hasSize(1);
        SshEndpointConfig ssh = config.sshEndpoints().get(0);
        assertThat(ssh.id()).isEqualTo("test");
        assertThat(ssh.host()).isEqualTo("127.0.0.1");
        assertThat(ssh.port()).isEqualTo(22);
        assertThat(ssh.auth()).isInstanceOf(SshAuthConfig.PasswordAuth.class);
    }

    @Test
    void missingIsServerIsRejected() {
        String yaml = "registerIp: 127.0.0.1\nregisterPort: 7000\n";
        assertThatThrownBy(() -> ProxyConfigLoader.loadFromYaml(yaml, Map.of(), Map.of()))
                .isInstanceOf(ConfigValidationException.class)
                .satisfies(e -> {
                    List<String> errors = ((ConfigValidationException) e).errors();
                    assertThat(errors).anyMatch(msg -> msg.contains("isServer"));
                });
    }

    @Test
    void duplicateRemotePortIsRejected() {
        String yaml = """
                isServer: false
                registerIp: 127.0.0.1
                registerPort: 7000
                proxyInfos:
                  - ip: 127.0.0.1
                    port: 7002
                    remotePort: 7001
                  - ip: 127.0.0.1
                    port: 7003
                    remotePort: 7001
                """;
        assertThatThrownBy(() -> ProxyConfigLoader.loadFromYaml(yaml, Map.of(), Map.of()))
                .isInstanceOf(ConfigValidationException.class)
                .satisfies(e -> {
                    List<String> errors = ((ConfigValidationException) e).errors();
                    assertThat(errors).anyMatch(msg -> msg.contains("remotePort duplicated"));
                });
    }

    @Test
    void unknownSshIdIsRejected() {
        String yaml = """
                isServer: true
                registerIp: 127.0.0.1
                registerPort: 7000
                serverInfos:
                  - type: ssh
                    port: 8848
                    sshId: missing
                    forwardIp: 127.0.0.1
                    forwardPort: 8848
                sshInfos:
                  - id: test
                    ip: 127.0.0.1
                    username: root
                    password: root
                """;
        assertThatThrownBy(() -> ProxyConfigLoader.loadFromYaml(yaml, Map.of(), Map.of()))
                .isInstanceOf(ConfigValidationException.class)
                .satisfies(e -> {
                    List<String> errors = ((ConfigValidationException) e).errors();
                    assertThat(errors).anyMatch(msg -> msg.contains("sshId not found"));
                });
    }

    @Test
    void passwordAndKeyBothConfiguredIsRejected() {
        String yaml = """
                isServer: true
                registerIp: 127.0.0.1
                registerPort: 7000
                sshInfos:
                  - id: test
                    ip: 127.0.0.1
                    username: root
                    password: secret
                    privateKeyPath: /tmp/id_rsa
                """;
        assertThatThrownBy(() -> ProxyConfigLoader.loadFromYaml(yaml, Map.of(), Map.of()))
                .isInstanceOf(ConfigValidationException.class)
                .satisfies(e -> {
                    List<String> errors = ((ConfigValidationException) e).errors();
                    assertThat(errors).anyMatch(msg -> msg.contains("only one of password"));
                });
    }

    @Test
    void cliOverrideBeatsYamlPassword() {
        String yaml = """
                isServer: true
                registerIp: 127.0.0.1
                registerPort: 7000
                sshInfos:
                  - id: test
                    ip: 127.0.0.1
                    username: root
                    password: in-yaml
                """;
        AppConfig config = ProxyConfigLoader.loadFromYaml(yaml, Map.of(),
                Map.of("ssh.password.test", "from-cli"));
        SshAuthConfig.PasswordAuth auth = (SshAuthConfig.PasswordAuth) config.sshEndpoints().get(0).auth();
        assertThat(auth.password()).isEqualTo("from-cli");
    }

    @Test
    void envVarBeatsYamlPassword() {
        String yaml = """
                isServer: true
                registerIp: 127.0.0.1
                registerPort: 7000
                sshInfos:
                  - id: test
                    ip: 127.0.0.1
                    username: root
                    password: in-yaml
                    passwordEnv: LZH_PROXY_SSH_TEST_PASSWORD
                """;
        AppConfig config = ProxyConfigLoader.loadFromYaml(yaml, Map.of("LZH_PROXY_SSH_TEST_PASSWORD", "from-env"),
                Map.of());
        SshAuthConfig.PasswordAuth auth = (SshAuthConfig.PasswordAuth) config.sshEndpoints().get(0).auth();
        assertThat(auth.password()).isEqualTo("from-env");
    }
}
