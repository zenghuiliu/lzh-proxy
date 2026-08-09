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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lzh.proxy.config.SecurityConfig.HostKeyPolicy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

/**
 * YAML 配置加载器：解析为 DTO、映射为不可变 {@link AppConfig} 并做聚合校验。
 *
 * <p>替代原 GlobalConfig 单例。加载失败以 {@link ConfigValidationException} 抛出，
 * 绝不调用 System.exit。</p>
 */
public final class ProxyConfigLoader {

    private static final ObjectMapper MAPPER;

    static {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        MAPPER = new ObjectMapper(yamlFactory);
        MAPPER.findAndRegisterModules();
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private ProxyConfigLoader() {
    }

    /** 从系统环境加载默认 profile（application.yml）。 */
    public static AppConfig load() {
        return load("application", System.getenv(), Map.of());
    }

    /** 从系统环境加载指定 profile。 */
    public static AppConfig load(String profile) {
        return load(profile, System.getenv(), Map.of());
    }

    /**
     * 加载指定 profile 的配置。
     *
     * @param profile       profile 名，如 "server" 对应 application-server.yml
     * @param env           环境变量快照（测试可注入）
     * @param cliOverrides  CLI 覆盖，如 {"ssh.password.test": "xxx"}
     */
    public static AppConfig load(String profile, Map<String, String> env, Map<String, String> cliOverrides) {
        String filename = profile == null || profile.isBlank() || "application".equals(profile)
                ? "application.yml"
                : "application-" + profile + ".yml";
        ConfigDto dto = readDto(filename);
        return build(dto, new SecretsResolver(env, cliOverrides));
    }

    /** 从 YAML 字符串加载（单元测试用）。 */
    public static AppConfig loadFromYaml(String yaml, Map<String, String> env, Map<String, String> cliOverrides) {
        try {
            ConfigDto dto = MAPPER.readValue(yaml, ConfigDto.class);
            return build(dto, new SecretsResolver(env, cliOverrides));
        } catch (IOException e) {
            throw new ConfigValidationException(List.of("failed to parse yaml: " + e.getMessage()));
        }
    }

    private static ConfigDto readDto(String filename) {
        String resourceName = filename.startsWith("/") ? filename : "/" + filename;
        Path inCurrentDir = Paths.get(System.getProperty("user.dir"), resourceName);
        try (InputStream in = Files.exists(inCurrentDir)
                ? Files.newInputStream(inCurrentDir)
                : ProxyConfigLoader.class.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new ConfigValidationException(List.of("config file not found: " + filename));
            }
            return MAPPER.readValue(in, ConfigDto.class);
        } catch (ConfigValidationException e) {
            throw e;
        } catch (IOException e) {
            throw new ConfigValidationException(List.of("failed to parse config file " + filename + ": " + e.getMessage()));
        }
    }

    private static AppConfig build(ConfigDto dto, SecretsResolver secrets) {
        List<String> errors = new ArrayList<>();

        // ---- 角色 ----
        Role role;
        if (dto.isServer == null) {
            errors.add("isServer is required (true for server, false for client)");
            role = Role.CLIENT;
        } else {
            role = dto.isServer ? Role.SERVER : Role.CLIENT;
        }

        // ---- 注册通道 ----
        String registerIp = trimToNull(dto.registerIp);
        if (registerIp == null) {
            errors.add("registerIp is required");
        }
        int registerPort = dto.registerPort == null ? 7000 : dto.registerPort;
        if (registerPort < 1 || registerPort > 65535) {
            errors.add("registerPort must be 1-65535: " + registerPort);
        }

        // ---- SSH 连接 ----
        List<SshEndpointConfig> sshEndpoints = new ArrayList<>();
        Set<String> sshIds = new HashSet<>();
        if (dto.sshInfos != null) {
            for (SshInfoDto s : dto.sshInfos) {
                String id = trimToNull(s.id);
                if (id == null) {
                    errors.add("sshInfos.id is required");
                    continue;
                }
                if (!sshIds.add(id)) {
                    errors.add("sshInfos.id duplicated: " + id);
                }
                if (trimToNull(s.ip) == null) {
                    errors.add("sshInfos[" + id + "].ip is required");
                }
                int port = s.port == null ? 22 : s.port;
                if (port < 1 || port > 65535) {
                    errors.add("sshInfos[" + id + "].port must be 1-65535: " + port);
                }
                if (trimToNull(s.username) == null) {
                    errors.add("sshInfos[" + id + "].username is required");
                }

                boolean hasPassword = trimToNull(s.password) != null || trimToNull(s.passwordEnv) != null;
                boolean hasKey = trimToNull(s.privateKeyPath) != null;
                SshAuthConfig auth = null;
                if (hasPassword && hasKey) {
                    errors.add("sshInfos[" + id + "]: configure only one of password/passwordEnv OR privateKeyPath");
                } else if (hasKey) {
                    String passphrase = secrets.resolveSecret(id, "passphrase", s.passphrase, s.passphraseEnv);
                    auth = new SshAuthConfig.KeyAuth(s.privateKeyPath, passphrase);
                } else if (hasPassword) {
                    String password = secrets.resolveSecret(id, "password", s.password, s.passwordEnv);
                    auth = new SshAuthConfig.PasswordAuth(password);
                } else {
                    errors.add("sshInfos[" + id + "]: at least one auth method required (password or privateKeyPath)");
                }

                int connectTimeout = s.connectTimeoutMs == null ? 10_000 : s.connectTimeoutMs;
                if (connectTimeout <= 0) {
                    errors.add("sshInfos[" + id + "].connectTimeoutMs must be positive");
                }
                int keepAliveInterval = s.keepAliveIntervalSec == null ? 30 : s.keepAliveIntervalSec;
                if (keepAliveInterval <= 0) {
                    errors.add("sshInfos[" + id + "].keepAliveIntervalSec must be positive");
                }
                int keepAliveCount = s.keepAliveCountMax == null ? 3 : s.keepAliveCountMax;
                if (keepAliveCount <= 0) {
                    errors.add("sshInfos[" + id + "].keepAliveCountMax must be positive");
                }

                if (auth != null) {
                    sshEndpoints.add(new SshEndpointConfig(id, s.ip, port, s.username, auth,
                            connectTimeout, keepAliveInterval, keepAliveCount));
                }
            }
        }

        // ---- 服务端暴露端口 ----
        List<ServerEndpoint> serverEndpoints = new ArrayList<>();
        Set<Integer> serverPorts = new HashSet<>();
        if (dto.serverInfos != null) {
            for (ServerInfoDto si : dto.serverInfos) {
                EndpointType type;
                String typeStr = trimToNull(si.type);
                if (typeStr == null || typeStr.equalsIgnoreCase("tcp")) {
                    type = EndpointType.TCP;
                } else if (typeStr.equalsIgnoreCase("ssh")) {
                    type = EndpointType.SSH;
                } else {
                    errors.add("serverInfos.type must be tcp or ssh: " + typeStr);
                    continue;
                }
                if (si.port == null) {
                    errors.add("serverInfos.port is required");
                    continue;
                }
                if (si.port < 1 || si.port > 65535) {
                    errors.add("serverInfos.port must be 1-65535: " + si.port);
                }
                if (!serverPorts.add(si.port)) {
                    errors.add("serverInfos.port duplicated: " + si.port);
                }
                if (type == EndpointType.SSH) {
                    if (trimToNull(si.sshId) == null) {
                        errors.add("serverInfos[" + si.port + "].sshId is required for ssh type");
                    }
                    if (trimToNull(si.forwardIp) == null) {
                        errors.add("serverInfos[" + si.port + "].forwardIp is required for ssh type");
                    }
                    if (si.forwardPort == null || si.forwardPort < 1 || si.forwardPort > 65535) {
                        errors.add("serverInfos[" + si.port + "].forwardPort must be 1-65535");
                    }
                }
                serverEndpoints.add(new ServerEndpoint(type, si.ip, si.port, si.sshId, si.forwardIp, si.forwardPort));
            }
        }
        for (ServerEndpoint e : serverEndpoints) {
            if (e.type() == EndpointType.SSH && e.sshId() != null && !sshIds.contains(e.sshId())) {
                errors.add("serverInfos[" + e.port() + "].sshId not found in sshInfos: " + e.sshId());
            }
        }

        // ---- 客户端代理绑定 ----
        List<ProxyBinding> proxyBindings = new ArrayList<>();
        Set<Integer> remotePorts = new HashSet<>();
        if (dto.proxyInfos != null) {
            for (ProxyInfoDto p : dto.proxyInfos) {
                boolean valid = true;
                if (trimToNull(p.ip) == null) {
                    errors.add("proxyInfos.ip is required");
                    valid = false;
                }
                if (p.port == null || p.port < 1 || p.port > 65535) {
                    errors.add("proxyInfos.port must be 1-65535: " + p.port);
                    valid = false;
                }
                if (p.remotePort == null || p.remotePort < 1 || p.remotePort > 65535) {
                    errors.add("proxyInfos.remotePort must be 1-65535: " + p.remotePort);
                    valid = false;
                }
                if (p.remotePort != null && !remotePorts.add(p.remotePort)) {
                    errors.add("proxyInfos.remotePort duplicated: " + p.remotePort);
                }
                if (valid) {
                    proxyBindings.add(new ProxyBinding(p.ip, p.port, p.remotePort));
                }
            }
        }

        // ---- 安全 / 管理 ----
        HostKeyPolicy hostKeyPolicy = HostKeyPolicy.TOFU_KNOWN_HOSTS;
        String hk = dto.security == null ? null : trimToNull(dto.security.hostKeyPolicy);
        if (hk != null) {
            try {
                hostKeyPolicy = HostKeyPolicy.valueOf(hk.toUpperCase());
            } catch (IllegalArgumentException ex) {
                errors.add("security.hostKeyPolicy must be one of " + Arrays.toString(HostKeyPolicy.values()) + ": " + hk);
            }
        }
        Path knownHosts = (dto.security == null || trimToNull(dto.security.knownHostsPath) == null)
                ? Path.of(".lzh-proxy", "known_hosts")
                : Path.of(dto.security.knownHostsPath);
        boolean managementEnabled = dto.management != null && Boolean.TRUE.equals(dto.management.enabled);
        int managementPort = (dto.management == null || dto.management.port == null) ? 7003 : dto.management.port;
        if (managementPort < 1 || managementPort > 65535) {
            errors.add("management.port must be 1-65535: " + managementPort);
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException(errors);
        }

        return new AppConfig(role,
                new RegisterConfig(registerIp, registerPort),
                serverEndpoints,
                proxyBindings,
                sshEndpoints,
                new SecurityConfig(hostKeyPolicy, knownHosts),
                new ManagementConfig(managementEnabled, managementPort));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ---- YAML DTO（保持现有 application*.yml 字段名不变） ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ConfigDto {
        public Boolean isServer;
        public String registerIp;
        public Integer registerPort;
        public List<ServerInfoDto> serverInfos;
        public List<ProxyInfoDto> proxyInfos;
        public List<SshInfoDto> sshInfos;
        public SecurityDto security;
        public ManagementDto management;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ServerInfoDto {
        public String type;
        public String ip;
        public Integer port;
        public String sshId;
        public String forwardIp;
        public Integer forwardPort;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ProxyInfoDto {
        public String ip;
        public Integer port;
        public Integer remotePort;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SshInfoDto {
        public String id;
        public String ip;
        public Integer port;
        public String username;
        public String password;
        public String passwordEnv;
        public String privateKeyPath;
        public String passphrase;
        public String passphraseEnv;
        public Integer connectTimeoutMs;
        public Integer keepAliveIntervalSec;
        public Integer keepAliveCountMax;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SecurityDto {
        public String hostKeyPolicy;
        public String knownHostsPath;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ManagementDto {
        public Boolean enabled;
        public Integer port;
    }
}
