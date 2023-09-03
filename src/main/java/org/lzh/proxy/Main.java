package org.lzh.proxy;

import java.util.concurrent.atomic.AtomicLong;

import org.lzh.proxy.client.Client;
import org.lzh.proxy.config.GlobalConfig;
import org.lzh.proxy.register.Register;
import org.lzh.proxy.server.Server;
import org.lzh.proxy.server.ssh.SSHClient;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import cn.hutool.cron.timingwheel.SystemTimer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    public static final SystemTimer SYSTEM_TIMER = new SystemTimer();
    public static final SSHClient sshClient = new SSHClient();
    public static final Server server = new Server();
    public static final Register register = new Register();
    public static final Client client = new Client();
    public static final EventLoopGroup bossgroup = new NioEventLoopGroup();
    public static final EventLoopGroup workgroup = new NioEventLoopGroup();

    public static final AtomicLong serverChannelSerial = new AtomicLong(0);

    public static void main(String[] args) {
        String filename = "application.yml";
        for (String arg : args) {
            if (arg.trim().contains("-profile")) {
                String[] envs = arg.split("=");
                if (envs.length > 1) {
                    filename = "application-" + envs[1] + ".yml";
                }
            }
            if (arg.trim().contains("-log.level")) {
                handleLogLevel(arg);
            }
        }
        SYSTEM_TIMER.start();
        // 初始化全局配置文件
        GlobalConfig globalConfig = GlobalConfig.init(filename);
        // log.info("{}",globalConfig.toString());
        // 开启服务端
        if (globalConfig.getIsServer()) {
            sshClient.init();
            server.init();
            register.init();
        } else {
            Client.timer.start();
            client.init();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            SSHClient.disconnect();
            bossgroup.shutdownGracefully();
            workgroup.shutdownGracefully();
        }));
    }

    private static void handleLogLevel(String arg) {
        String[] levels = arg.split("=");
        if (levels.length > 1) {
            ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
            Logger logger = loggerFactory.getLogger("org.lzh.proxy");
            if (logger instanceof ch.qos.logback.classic.Logger) {
                ((ch.qos.logback.classic.Logger) logger).setLevel(Level.toLevel(levels[1]));
            }

        }
    }
}