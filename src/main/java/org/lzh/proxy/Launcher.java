package org.lzh.proxy;

import java.util.HashMap;
import java.util.Map;

import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.config.ConfigValidationException;
import org.lzh.proxy.config.ProxyConfigLoader;
import org.lzh.proxy.config.Role;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * 应用入口（替代原 Main）。
 *
 * <p>职责：解析命令行参数、加载并校验配置、按角色装配组合根、安装关闭钩子。
 * 库内代码不调用 System.exit——配置失败由本入口打印错误并返回非零退出码。</p>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        String profile = "application";
        String logLevel = null;
        Map<String, String> cliOverrides = new HashMap<>();

        for (String arg : args) {
            String t = arg.trim();
            if (t.startsWith("--")) {
                int idx = t.indexOf('=');
                if (idx > 0) {
                    cliOverrides.put(t.substring(2, idx), t.substring(idx + 1));
                }
            } else if (t.startsWith("-profile=")) {
                profile = t.substring("-profile=".length());
            } else if (t.startsWith("-log.level=")) {
                logLevel = t.substring("-log.level=".length());
            }
        }

        if (logLevel != null) {
            applyLogLevel(logLevel);
        }

        AppConfig config;
        try {
            config = ProxyConfigLoader.load(profile, System.getenv(), cliOverrides);
        } catch (ConfigValidationException e) {
            System.err.println("配置错误（共 " + e.errors().size() + " 项）：");
            for (String err : e.errors()) {
                System.err.println("  - " + err);
            }
            System.exit(2);
            return;
        }

        Lifecycle app = config.role() == Role.SERVER
                ? new ServerLauncher(config)
                : new ClientLauncher(config);

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop, "lzh-proxy-shutdown"));

        try {
            app.start();
        } catch (Exception e) {
            LoggerFactory.getLogger(Launcher.class).error("启动失败", e);
            app.stop();
            System.exit(1);
        }
    }

    /** 设置 org.lzh.proxy 包日志级别（命令行覆盖）。 */
    private static void applyLogLevel(String level) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        Logger logger = factory.getLogger("org.lzh.proxy");
        if (logger instanceof ch.qos.logback.classic.Logger lbLogger) {
            lbLogger.setLevel(Level.toLevel(level));
        }
    }
}
