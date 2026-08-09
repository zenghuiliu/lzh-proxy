# lzh-proxy

一个基于 Netty 的 TCP 代理程序，支持 TCP 数据转发与 SSH 跳板转发。分为**服务端**与**客户端**两个角色：服务端部署在公网主机上，监听注册端口与代理端口；内网机器安装客户端并向服务端注册，即可将公网访问转发到内网应用。

## 特性

- **双模式**：`isServer` 配置决定角色；`type: tcp` 走 Netty 代理链路，`type: ssh` 走 SSH 跳板转发（基于 Apache MINA SSHD）。
- **SSH 断连自动重建**：每条 SSH 连接由状态机管理（`DISCONNECTED / CONNECTING / CONNECTED / BACKOFF / STOPPED`），断开后指数退避 + 抖动自动重连，重连成功后自动重放所有端口转发。
- **主机密钥校验**：默认 TOFU（首次连接接受并记录 known_hosts），可选 STRICT 严格校验或 ACCEPT_ALL 逃生舱。
- **密钥安全**：SSH 密码/密钥口令可从环境变量或命令行注入，避免明文入配置。
- **背压**：控制通道写缓冲水位 + 写可控性联动叶子通道读暂停，防止慢消费者导致内存无界增长。
- **可观测性**：轻量管理 HTTP 端点（`/healthz` `/metrics` `/status`）与运行指标。
- **配置校验**：加载即校验（端口/引用关系/认证方式），错误聚合返回，绝不静默 `System.exit`。

## 环境要求

- JDK 21（构建：`JAVA_HOME` 指向 JDK 21）
- Maven 3.9+

## 构建与运行

```sh
# 构建（含单元测试 + 集成测试）
mvn verify

# 服务端（默认 application-server.yml 由 -profile=server 选择）
java -jar target/lzh-proxy.jar -profile=server

# 客户端（默认 application.yml）
java -jar target/lzh-proxy.jar

# 可选命令行参数
#   -profile=<name>      选择 application-<name>.yml
#   -log.level=<level>   设置 org.lzh.proxy 日志级别（DEBUG/INFO/...）
#   --ssh.password.<id>=<value>   命令行覆盖 SSH 密码
```

> 说明：进程通过关闭钩子优雅停机（先停 SSH、解绑监听、关闭隧道、再停事件循环）。

## 服务端配置（application-server.yml）

```yaml
isServer: true
# 注册通道监听地址
registerIp: 127.0.0.1
registerPort: 7000

# SSH 跳板连接列表
sshInfos:
  - id: test
    ip: 127.0.0.1
    port: 22
    username: root
    # 认证方式（二选一）：密码 或 私钥
    password: root
    # passwordEnv: LZH_PROXY_SSH_TEST_PASSWORD   # 优先于 password，从环境变量取
    # privateKeyPath: C:\Users\lzh\.ssh\id_rsa   # 密钥认证（可选 passphrase）
    # passphrase: 密钥口令
    # connectTimeoutMs: 10000      # 连接超时（毫秒，默认 10000）
    # keepAliveIntervalSec: 30     # 保活间隔（秒，默认 30）
    # keepAliveCountMax: 3         # 保活失败判定次数（默认 3）

# 服务端暴露端口列表
serverInfos:
  # type: tcp（默认，走 Netty 代理链路）或 ssh（SSH 跳板转发）
  - type: ssh
    ip: 127.0.0.1
    port: 8848
    sshId: test          # 引用 sshInfos.id
    forwardIp: 127.0.0.1 # 转发目标（仅 SSH 类型）
    forwardPort: 8848

# 管理端点（可选，默认关闭；仅回环监听）
management:
  enabled: true
  port: 7003

# 安全（可选）
# security:
#   hostKeyPolicy: tofu_known_hosts   # tofu_known_hosts（默认）/ strict / accept_all
#   knownHostsPath: .lzh-proxy/known_hosts
```

启动后：客户端注册某 `remotePort` 时，服务端会**懒绑定**对应代理端口。

## 客户端配置（application.yml）

```yaml
isServer: false
# 服务端注册地址
registerIp: 127.0.0.1
registerPort: 7000
# 需要代理的应用列表：ip/port 为客户端可达的被代理应用；remotePort 为请求服务端开放的端口
proxyInfos:
  - ip: 127.0.0.1
    port: 7002
    remotePort: 7001

# management / security 配置同服务端（可选）
```

## 管理端点

管理服务（需 `management.enabled: true`）默认监听回环地址：

| 端点 | 说明 |
|------|------|
| `GET /healthz` | 存活探针（停止中返回 503） |
| `GET /metrics` | 文本指标：隧道开/关/活动数、双向字节、控制通道重连、SSH 重连尝试/失败、注册拒绝 |
| `GET /status` | JSON 状态：角色、注册地址、隧道数、通道图、SSH 状态 |

## 架构概览

```
用户 ──> 服务端代理端口 ──(控制通道/序列关联)──> 客户端 ──> 内网应用
                     \__ SSH 跳板（type: ssh，经 SSH 服务器转发） __/
```

- **控制通道**：客户端向服务端注册的长连接，承载注册、心跳与按序列号关联的数据帧。
- **线协议**：`[4字节长度][1字节type][8字节serial][payload]`，服务端/客户端两侧保持逐字节兼容。

  | 消息类型 | 值 | 用途 |
  |---------|-----|------|
  | `CONNECT` | 0x01 | 服务端告知客户端建立到应用的连接 |
  | `DISCONNECT` | 0x02 | 通知对端关闭某序列号隧道 |
  | `TRANSFER` | 0x03 | 隧道数据传输 |
  | `REGISTER` | 0x05 | 客户端注册（payload 为 `appIp,appPort,remotePort\r\n`） |
  | `HEARTBEAT_PING` / `HEARTBEAT_PONG` | 0x06 / 0x07 | 控制通道心跳 |

- **模块**：`config`（不可变配置+校验）、`lifecycle`/`core`（组合根+生命周期）、`protocol`（消息类型+编解码）、`control`（控制通道处理器+注册协议）、`forward`（隧道注册表/转发/背压）、`tunnel/ssh`（MINA SSHD 状态机）、`management`（指标+管理端点）。

## 测试

```sh
mvn verify
```

- 单元测试（surefire，25 个）：
  - `ProxyConfigLoaderTest`：配置加载与校验（缺字段/重复端口/未知 sshId/双认证方式）、环境变量与 CLI 密钥覆盖。
  - `ProxyMessageCodecTest`：线协议 golden-bytes 逐字节互操作、编解码往返、畸形/超长帧拒绝（绝不 `NegativeArraySizeException`）。
  - `RegisterProtocolTest`：注册报文编解码与非法输入拒绝。
  - `ReconnectPolicyTest`：退避延迟区间、封顶、抖动边界。
- 集成测试（failsafe，`SshReconnectIT`）：嵌入式 MINA SSHD 服务器上建立本地转发，回显验证；**停止并同端口重启 SSH 服务器**，断言自动重连 + 转发重放后回显恢复。

## 配置校验规则

- 角色 `isServer` 必填；注册端口 1-65535。
- `serverInfos`：端口唯一；SSH 类型必须引用已存在的 `sshId` 并配置 `forwardIp/forwardPort`。
- `proxyInfos`：`remotePort` 唯一，应用 ip/端口必填。
- `sshInfos`：`id` 唯一、主机必填、恰好一种认证方式（密码或私钥）。
