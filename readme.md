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
    # 服务端暴露端口列表（isServer为false无需配置）
    serverInfos:
      # 服务类型：tcp（默认打开tcp端口）、ssh（ssh登录转发）
      - type: ssh
        ip: 127.0.0.1
        port: 2222
        # type类型为ssh时需要配置以下内容
        ssh: 
          # ssh登录信息
          ip: 127.0.0.1
          port: 22
          username: root
          password: root
          # ssh转发ip以及port
          forwardIp: 127.0.0.1
          forwardPort: 6379

    # 代理端口（isServer为true无需配置）
    proxyInfos:
      - ip: 127.0.0.1
        port: 28080
        remotePort: 7001

    ```
   - 1.2启动服务端：
    ``` sh
    java -jar lzh-proxy.jar
    ```
   - 1.3配置ssh登录转发

      如果需要代理的应用无法连接互联网，只有内网的一台堡垒机可以访问互联网，而这台堡垒机有外网IP，此时可以在serverInfos配置项下新增一个type为ssh的配置，服务端将会通过这台堡垒机的ssh通道转发能力代理出一个可以访问的端口，其他互联网机器可以通过服务器打开的本地端口建立连接并最终将请求代理到需要连接的应用服务。

- 2. 客户端配置
  - 2.1 修改配置文件application.yml
  设置isServer配置为false，配置好服务端监听的registerIp以及registerPort，同时将poxyInfos配置列表新增需要代理的应用服务器的ip、port，设置需要在服务端打开监听的remotePort端口

  ``` yaml

    isServer: false
    # 服务端注册ip以及port
    registerIp: 127.0.0.1
    registerPort: 7000
    # 服务端暴露端口列表（isServer为false无需配置）
    serverInfos:
      - ip: 127.0.0.1
        port: 7001
    # 代理端口（isServer为true无需配置）
    proxyInfos:
      - ip: 127.0.0.1
        port: 7002
        remotePort: 7001
        
    ```
    - 2.2 启动客户端：
    ``` sh
    java -jar lzh-proxy.jar
    ```
    启动完成后客户端会像服务端注册对应连接以及打开服务端相应端口，并完成通信链路建立
    
