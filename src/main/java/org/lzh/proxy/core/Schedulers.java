/*
 * Copyright 2023-2026 lzh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
