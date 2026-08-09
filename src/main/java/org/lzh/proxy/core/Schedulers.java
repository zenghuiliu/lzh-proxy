package org.lzh.proxy.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.util.NamedThreadFactory;

/**
 * 集中创建与管理应用级调度线程池。
 *
 * <p>替代原 Main.SYSTEM_TIMER（hutool 定时轮）：SSH 保活/重连等
 * 周期性任务统一使用命名调度线程池，停机时统一关闭。</p>
 */
public class Schedulers implements Lifecycle {

    private final ScheduledExecutorService sshKeepAlive =
            Executors.newScheduledThreadPool(2, new NamedThreadFactory("lzh-proxy-ssh"));

    /** SSH 保活 / 重连调度线程池。 */
    public ScheduledExecutorService sshKeepAlive() {
        return sshKeepAlive;
    }

    @Override
    public void start() {
        // 线程池在构造时即创建，此处无额外动作
    }

    @Override
    public void stop() {
        sshKeepAlive.shutdownNow();
    }
}
