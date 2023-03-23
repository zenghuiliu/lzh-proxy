# 1.项目介绍
一个简单的TCP代理程序，实现了TCP连接的数据代理转发，分为服务端和客户端，通过将服务端部署在代理服务器端（一般为有公网IP的主机）上，服务端会根据注册端口，打开注册监听，被代理的机器只需要安装客户端，配置注册IP以及端口即可根据需要向服务端注册并打开代理的端口，如此用户访问服务端相关端口即可将连接请求转发到客户端机器的相关应用服务上，完成服务代理
# 2.配置
- 1. 服务端配置

  - 1.1修改配置文件 application.yml

    如果是服务端请设置isServer为true，客户端设为false，服务端配置好自己的registerIp以及registerPort，其他留空就好
    ``` yaml
    isServer: true
    # 注册ip以及端口
    registerIp: 127.0.0.1
    registerPort: 7000
    # 需要预打开的服务端口列表
    serverInfos:
      - ip: 127.0.0.1
        port: 7001
    # 服务端无需关心
    proxyInfos:
      - ip: 127.0.0.1
        port: 7001
        remotePort: 7001
    ```
   - 1.2启动服务端：
    ``` sh
    java -jar lzh-proxy.jar
    ```
- 2. 客户端配置
  - 2.1 修改配置文件application.yml
  设置isServer配置为false，配置好服务端监听的registerIp以及registerPort，同时将poxyInfos配置列表新增需要代理的应用服务器的ip、port，设置需要在服务端打开监听的remotePort端口
  ``` yaml
    isServer: false
    # 服务端注册ip以及port
    registerIp: 127.0.0.1
    registerPort: 7000
    # 客户端无需关心
    serverInfos:
      - ip: 127.0.0.1
        port: 7001
    # 客户端代理服务列表
    proxyInfos:
      - ip: 127.0.0.1
        port: 7001
        remotePort: 7001
    ```
    - 2.2 启动客户端：
    ``` sh
    java -jar lzh-proxy.jar
    ```
    启动完成后客户端会像服务端注册对应连接以及打开服务端相应端口，并完成通信链路建立
    
