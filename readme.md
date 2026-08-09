# lzh-proxy

> 中英双语文档 · Bilingual README (中文 / English)

一个基于 Netty 的 TCP 代理程序，支持 TCP 数据转发与 SSH 跳板转发。分为**服务端**与**客户端**两个角色：服务端部署在公网主机上，监听注册端口与代理端口；内网机器安装客户端并向服务端注册，即可将公网访问转发到内网应用。

A Netty-based TCP proxy supporting TCP data forwarding and SSH jump-host tunneling. Two roles: a **server** on a public-IP host (listens on a registration port and proxy ports) and a **client** on the intranet machine that registers with the server, so public access to the server's ports is forwarded to the intranet application.

---

## 特性 / Features

- **双模式**：`isServer` 配置决定角色；`type: tcp` 走 Netty 代理链路，`type: ssh` 走 SSH 跳板转发（基于 Apache MINA SSHD）。
  **Dual mode**: the `isServer` flag selects the role; `type: tcp` uses the Netty proxy path, `type: ssh` uses SSH jump-host forwarding (Apache MINA SSHD).
- **SSH 断连自动重建**：每条 SSH 连接由状态机管理（`DISCONNECTED / CONNECTING / CONNECTED / BACKOFF / STOPPED`），断开后指数退避 + 抖动自动重连，重连成功后自动重放所有端口转发。
  **Auto-reconnect on SSH drop**: each SSH connection is a state machine; on disconnect it reconnects with exponential backoff + jitter and re-applies all port forwards.
- **主机密钥校验**：默认 TOFU（首次连接接受并记录 known_hosts），可选 STRICT 严格校验或 ACCEPT_ALL 逃生舱。
  **Host key verification**: TOFU by default (accept-and-record on first connect), with STRICT or ACCEPT_ALL options.
- **密钥安全**：SSH 密码/密钥口令可从环境变量或命令行注入，避免明文入配置。
  **Secret handling**: SSH passwords/passphrases can come from environment variables or CLI, keeping them out of plaintext config.
- **背压**：控制通道写缓冲水位 + 写可控性联动叶子通道读暂停，防止慢消费者导致内存无界增长。
  **Backpressure**: control-channel write-buffer watermarks + writability-driven AUTO_READ pausing of leaf channels bound memory.
- **可观测性**：轻量管理 HTTP 端点（`/healthz` `/metrics` `/status`）与运行指标。
  **Observability**: lightweight admin HTTP endpoints (`/healthz`, `/metrics`, `/status`) and runtime metrics.
- **配置校验**：加载即校验（端口/引用关系/认证方式），错误聚合返回，绝不静默 `System.exit`。
  **Config validation**: validated at load (ports / references / auth method) with aggregated errors; never a silent `System.exit`.

## 环境要求 / Requirements

- JDK 21（构建：`JAVA_HOME` 指向 JDK 21）· JDK 21 (set `JAVA_HOME` to JDK 21 to build)
- Maven 3.9+

## 构建与运行 / Build & Run

```sh
# 构建（含单元测试 + 集成测试）· build (unit + integration tests)
mvn verify

# 服务端 · server（默认 application-server.yml，由 -profile=server 选择）
java -jar target/lzh-proxy.jar -profile=server

# 客户端 · client（默认 application.yml）
java -jar target/lzh-proxy.jar

# 可选命令行参数 · optional CLI args
#   -profile=<name>      选择 application-<name>.yml · select application-<name>.yml
#   -log.level=<level>   设置日志级别 · set org.lzh.proxy log level (DEBUG/INFO/...)
#   --ssh.password.<id>=<value>   命令行覆盖 SSH 密码 · override SSH password
```

> 进程通过关闭钩子优雅停机（先停 SSH、解绑监听、关闭隧道、再停事件循环）。
> Graceful shutdown via JVM shutdown hook (stop SSH → unbind listeners → close tunnels → stop event loops).

## 服务端配置 / Server Config（application-server.yml）

```yaml
isServer: true
# 注册通道监听地址 · registration listener address
registerIp: 127.0.0.1
registerPort: 7000

# SSH 跳板连接列表 · SSH jump-host connections
sshInfos:
  - id: test
    ip: 127.0.0.1
    port: 22
    username: root
    # 认证方式（二选一）· auth method (pick one): password or private key
    password: root
    # passwordEnv: LZH_PROXY_SSH_TEST_PASSWORD   # 优先于 password · overrides password, read from env
    # privateKeyPath: C:\Users\lzh\.ssh\id_rsa   # 密钥认证（可选 passphrase）· key auth (optional passphrase)
    # passphrase: 密钥口令 · key passphrase
    # connectTimeoutMs: 10000      # 连接超时（毫秒，默认 10000）· connect timeout ms
    # keepAliveIntervalSec: 30     # 保活间隔（秒，默认 30）· keepalive interval s
    # keepAliveCountMax: 3         # 保活失败判定次数（默认 3）· keepalive count max

# 服务端暴露端口列表 · exposed ports
serverInfos:
  # type: tcp（默认，Netty 代理链路）或 ssh（SSH 跳板转发）· tcp (default) or ssh
  - type: ssh
    ip: 127.0.0.1
    port: 8848
    sshId: test          # 引用 sshInfos.id · reference to sshInfos.id
    forwardIp: 127.0.0.1 # 转发目标（仅 SSH 类型）· forward target (ssh only)
    forwardPort: 8848

# 管理端点（可选，默认关闭；仅回环监听）· admin (optional, off by default; loopback only)
management:
  enabled: true
  port: 7003

# 安全（可选）· security (optional)
# security:
#   hostKeyPolicy: tofu_known_hosts   # tofu_known_hosts（默认）/ strict / accept_all
#   knownHostsPath: .lzh-proxy/known_hosts
```

> 客户端注册某 `remotePort` 时，服务端会**懒绑定**对应代理端口。
> When a client registers a `remotePort`, the server **lazily binds** that proxy port.

## 客户端配置 / Client Config（application.yml）

```yaml
isServer: false
# 服务端注册地址 · server registration address
registerIp: 127.0.0.1
registerPort: 7000
# 需要代理的应用列表：ip/port 为客户端可达的应用；remotePort 为请求服务端开放的端口
# apps to proxy: ip/port reachable from the client; remotePort is the port requested on the server
proxyInfos:
  - ip: 127.0.0.1
    port: 7002
    remotePort: 7001

# management / security 配置同服务端（可选）· same as server (optional)
```

## 管理端点 / Management Endpoints

管理服务（需 `management.enabled: true`）默认监听回环地址 · Admin server (requires `management.enabled: true`), loopback by default:

| 端点 / Endpoint | 说明 / Description |
|------|------|
| `GET /healthz` | 存活探针（停止中返回 503）· liveness probe (503 while stopping) |
| `GET /metrics` | 文本指标 · plain-text metrics: tunnels opened/closed/active, bidirectional bytes, control reconnects, SSH reconnect attempts/failures, register rejects |
| `GET /status` | JSON 状态 · JSON status: role, register address, tunnel counts, channel maps, SSH states |

## 架构概览 / Architecture

```
用户 ──> 服务端代理端口 ──(控制通道/序列关联)──> 客户端 ──> 内网应用
User ──> Server proxy port ──(control channel / serial)──> Client ──> Intranet app
                     \__ SSH 跳板（type: ssh，经 SSH 服务器转发）
                         SSH jump (via SSH server), type: ssh
```

- **控制通道**：客户端向服务端注册的长连接，承载注册、心跳与按序列号关联的数据帧。
  **Control channel**: the long-lived registration connection, carrying registration, heartbeats and serial-correlated data frames.
- **线协议**：`[4字节长度][1字节type][8字节serial][payload]`，两侧逐字节兼容。
  **Wire protocol**: `[4-byte length][1-byte type][8-byte serial][payload]`, byte-for-byte compatible across peers.

  | 消息类型 / Message | 值 / Value | 用途 / Purpose |
  |---------|-----|------|
  | `CONNECT` | 0x01 | 服务端告知客户端建立到应用的连接 · ask client to connect to the app |
  | `DISCONNECT` | 0x02 | 通知对端关闭某序列号隧道 · close a tunnel by serial |
  | `TRANSFER` | 0x03 | 隧道数据传输 · tunnel data |
  | `REGISTER` | 0x05 | 客户端注册（payload：`appIp,appPort,remotePort\r\n`）· client registration |
  | `HEARTBEAT_PING` / `HEARTBEAT_PONG` | 0x06 / 0x07 | 控制通道心跳 · control-channel heartbeat |

- **模块 / Packages**：`config`（不可变配置+校验 · immutable config + validation）、`lifecycle`/`core`（组合根+生命周期 · composition root + lifecycle）、`protocol`（消息类型+编解码 · message types + codec）、`control`（控制通道处理器+注册协议 · control-channel handlers + register protocol）、`forward`（隧道注册表/转发/背压 · tunnel registry / forwarding / backpressure）、`tunnel/ssh`（MINA SSHD 状态机 · state machine）、`management`（指标+管理端点 · metrics + admin endpoints）。

## 测试 / Testing

```sh
mvn verify
```

- 单元测试（surefire，25 个）· unit tests (25):
  - `ProxyConfigLoaderTest`：配置加载与校验（缺字段/重复端口/未知 sshId/双认证方式）、环境变量与 CLI 密钥覆盖。Config loading & validation; env/CLI secret override.
  - `ProxyMessageCodecTest`：线协议 golden-bytes 逐字节互操作、编解码往返、畸形/超长帧拒绝（绝不 `NegativeArraySizeException`）。Wire-protocol golden-bytes interop, round-trip, malformed/oversized frame rejection.
  - `RegisterProtocolTest`：注册报文编解码与非法输入拒绝。Register payload encode/parse and invalid-input rejection.
  - `ReconnectPolicyTest`：退避延迟区间、封顶、抖动边界。Backoff delay ranges, capping, jitter bounds.
- 集成测试（failsafe，`SshReconnectIT`）· integration test: embedded MINA SSHD server + local forward + echo verification; **stop and restart the SSH server on the same port**, assert auto-reconnect and forward replay restore the echo.

## 配置校验规则 / Validation Rules

- 角色 `isServer` 必填；注册端口 1-65535。Role `isServer` required; register port 1-65535.
- `serverInfos`：端口唯一；SSH 类型必须引用已存在的 `sshId` 并配置 `forwardIp/forwardPort`。Ports unique; SSH type must reference an existing `sshId` and set `forwardIp/forwardPort`.
- `proxyInfos`：`remotePort` 唯一，应用 ip/端口必填。`remotePort` unique; app ip/port required.
- `sshInfos`：`id` 唯一、主机必填、恰好一种认证方式（密码或私钥）。`id` unique, host required, exactly one auth method (password or key).
