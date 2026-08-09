package org.lzh.proxy.config;

/**
 * 管理/可观测端点配置。
 *
 * @param enabled 是否启用管理 HTTP 服务
 * @param port    管理服务监听端口（默认 7003，仅回环）
 */
public record ManagementConfig(boolean enabled, int port) {
}
