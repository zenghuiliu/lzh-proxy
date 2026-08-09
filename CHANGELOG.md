# Changelog

本项目基于社区版 lzh-proxy 重构而来，v1.0.0 为首个公共库正式版本。

## [1.0.0] - 2026-08-09

### Added
- 公共门面 API `LzhProxy`：builder 式编程入口，可嵌入任意应用（`LzhProxy.server()/client()` + `AutoCloseable` 实例）。
- 完整架构重构（相对原版）：
  - JDK 21 + 不可变配置模型 `AppConfig` + 加载即校验（`ProxyConfigLoader`），secret 支持环境变量/CLI 注入。
  - 组合根 + 生命周期管理（`Lifecycle`/`LifecycleRegistry`），有序优雅停机。
  - SSH 层基于 Apache MINA SSHD 重写：显式状态机 + 指数退避抖动自动重连 + 重连后转发重放 + 主机密钥校验（TOFU/STRICT/ACCEPT_ALL）+ 密码/密钥认证。
  - 协议层 `MessageType` 枚举 + `ProxyMessage` record，解码器加固（畸形/超长帧拒绝）。
  - 背压：控制通道写缓冲水位 + 写可控性联动叶子通道读暂停。
  - 可观测性：`MetricsRegistry` + 管理 HTTP 端点（`/healthz` `/metrics` `/status`）。
- 测试：25 个单元测试（配置校验/线协议 golden-bytes/注册报文/退避策略）+ 集成测试 `SshReconnectIT`（嵌入式 SSHD 断连重建）。
- CI：GitHub Actions（JDK 21 构建验证 + tag 触发 Central 发布）。
- 文档：中英双语 README、配置 schema、发布说明。

### Changed
- 线协议保持与原版**逐字节兼容**；服务端/客户端同一 jar，`isServer` 决定角色。
- 独立运行使用 fat jar `lzh-proxy-jar-with-dependencies.jar`；库依赖使用 slim jar `lzh-proxy.jar`。

### Removed
- 原 JSch 依赖（替换为 Apache MINA SSHD）、Lombok、Hutool、commons-lang3。
- 全局静态状态（原 `Main`/`GlobalConfig`/`ChannelChache` 单例）。

### Security
- 默认主机密钥 TOFU 校验（替代原 `StrictHostKeyChecking=no`）。
- SSH 密码/密钥口令可经环境变量/CLI 注入，避免明文入配置。
