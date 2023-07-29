package org.lzh.proxy.server;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.lzh.proxy.Main;
import org.lzh.proxy.config.ChannelChache;
import org.lzh.proxy.config.GlobalConfig;
import org.lzh.proxy.config.GlobalConfig.SSHInfo;
import org.lzh.proxy.config.GlobalConfig.ServerInfo;
import org.lzh.proxy.server.handler.ServerDataHandler;
import org.lzh.proxy.server.ssh.SSHClient;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Server {
    ServerBootstrap serverBootstrap = new ServerBootstrap();

    public void init() {
        serverBootstrap.group(Main.bossgroup, Main.workgroup);
        serverBootstrap.channel(NioServerSocketChannel.class);
        // 设置tcp连接队列长度
        serverBootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        // SO_SNDBUF发送缓冲区，SO_RCVBUF接收缓冲区，SO_KEEPALIVE开启TCP连接保持
        serverBootstrap.childOption(ChannelOption.SO_SNDBUF, 16 * 1024)
                .childOption(ChannelOption.SO_RCVBUF, 16 * 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new ServerDataHandler());
            }
        });
        List<GlobalConfig.ServerInfo> serverInfos = GlobalConfig.getInstance().getServerInfos();
        if (serverInfos != null) {
            for (GlobalConfig.ServerInfo serverInfo : GlobalConfig.getInstance().getServerInfos()) {
                if(StringUtils.isBlank(serverInfo.getType()) || serverInfo.getType().equalsIgnoreCase("tcp")){
                    bindPort(serverInfo);
                }else if(serverInfo.getType().equalsIgnoreCase("ssh")){
                    sshForward(serverInfo);
                }
            }
        }
    }

    private void sshForward(ServerInfo serverInfo) {
        try {
            SSHInfo sshInfo = serverInfo.getSsh();
            SSHClient.forwardLocal(SSHClient.connectSession(sshInfo.getIp(), sshInfo.getPort(), sshInfo.getUsername(), sshInfo.getPassword())
            ,serverInfo.getPort(), sshInfo.getForwardIp(), sshInfo.getForwardPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void bindPort(GlobalConfig.ServerInfo serverInfo) {
        serverBootstrap.bind(serverInfo.getPort()).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Channel channel = future.channel();
                if (channel.isOpen()) {
                    ChannelChache.portToServerMap.put(serverInfo.getPort(), channel);
                    log.info("{} server started !", channel.localAddress().toString());
                } else {
                    log.error("{} server start error !", channel.localAddress().toString());
                }
            }
        });
    }

}
