package org.lzh.proxy.register;

import org.lzh.proxy.Main;
import org.lzh.proxy.config.Constants;
import org.lzh.proxy.config.GlobalConfig;
import org.lzh.proxy.protocol.ProxyMessageDecoder;
import org.lzh.proxy.protocol.ProxyMessageEncoder;
import org.lzh.proxy.register.handler.RegisterDataHandler;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Register {
    ServerBootstrap serverBootstrap = new ServerBootstrap();
    public void init() {
        serverBootstrap.group(Main.bossgroup, Main.workgroup);
        serverBootstrap.channel(NioServerSocketChannel.class);
        //下面的代码为缓冲区设置,其实是非必要代码,可以不用设置,也可以根据自己需求设置参数
        serverBootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        // SO_SNDBUF发送缓冲区，SO_RCVBUF接收缓冲区，SO_KEEPALIVE开启心跳监测（保证连接有效）
        serverBootstrap.childOption(ChannelOption.SO_SNDBUF, 16 * 1024)
                .childOption(ChannelOption.SO_RCVBUF, 16 * 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                /*pipeline.addLast("line",new LineBasedFrameDecoder(8192));
                pipeline.addLast("string",new StringDecoder());*/
                pipeline.addLast(new ProxyMessageDecoder(Constants.MAX_FRAME_LENGTH, Constants.LENGTH_FIELD_OFFSET, Constants.LENGTH_FIELD_LENGTH, Constants.LENGTH_ADJUSTMENT, Constants.INITIAL_BYTES_TO_STRIP));
                pipeline.addLast(new ProxyMessageEncoder());
                pipeline.addLast(new RegisterDataHandler());
            }
        });

        serverBootstrap.bind(GlobalConfig.getInstance().getRegisterPort()).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Channel channel = future.channel();
                if (channel.isOpen()){
                    log.info("{} register started !",channel.localAddress().toString());
                }else {
                    log.error("{} register start error !",channel.localAddress().toString());
                }
            }
        });
    }


}
