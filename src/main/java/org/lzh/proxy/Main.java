package org.lzh.proxy;

import ch.qos.logback.classic.Level;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;

import org.lzh.proxy.client.Client;
import org.lzh.proxy.config.GlobalConfig;
import org.lzh.proxy.register.Register;
import org.lzh.proxy.server.Server;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class Main {
    public static Server server = new Server();
    public static Register register = new Register();
    public static Client client = new Client();
    public static EventLoopGroup bossgroup = new NioEventLoopGroup();;
    public static EventLoopGroup workgroup = new NioEventLoopGroup();

    public static AtomicLong serverChannelSerial = new AtomicLong(0);

    public static void main(String[] args) {
        String filename = "application.yml";
        for (String arg : args){
            if (arg.trim().contains("-profile")){
                String[] envs = arg.split("=");
                if (envs.length > 1) {
                    filename = "application-" + envs[1] + ".yml";
                }
            }
            if (arg.trim().contains("-log.level")){
                handleLogLevel(arg);
            }
        }
        // 初始化全局配置文件
        GlobalConfig globalConfig = GlobalConfig.init(filename);
        // log.info("{}",globalConfig.toString());
        // 开启服务端
        if (globalConfig.getIsServer()){
            server.init();
            register.init();
        }else {
            Client.timer.start();
            client.init();
        }

    }

    private static void handleLogLevel(String arg) {
        String[] levels = arg.split("=");
        if (levels.length > 1){
            ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
            Logger logger = loggerFactory.getLogger("org.lzh.proxy");
            if (logger instanceof ch.qos.logback.classic.Logger){
                ((ch.qos.logback.classic.Logger) logger).setLevel(Level.toLevel(levels[1]));
            }

        }
    }
}