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
package org.lzh.proxy.tunnel.ssh;

import java.util.Random;

/**
 * 指数退避 + 全抖动延迟计算（纯函数，可单测）。
 *
 * <pre>
 *   delay = min(maxMs, baseMs &lt;&lt; (attempt - 1))
 *   delay *= (1 - jitter) + jitter * random.nextDouble()
 * </pre>
 */
public class ReconnectPolicy {

    private final long baseDelayMs;
    private final long maxDelayMs;
    private final double jitter;
    private final Random random;

    public ReconnectPolicy(long baseDelayMs, long maxDelayMs, double jitter, Random random) {
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.jitter = jitter;
        this.random = random;
    }

    /** 默认：base 1s、max 60s、jitter 0.2。 */
    public static ReconnectPolicy defaults() {
        return new ReconnectPolicy(1000, 60_000, 0.2, new Random());
    }

    /**
     * 计算第 {@code attempt} 次重连的等待毫秒数（attempt 从 1 开始）。
     */
    public long nextDelayMs(int attempt) {
        int shift = Math.min(Math.max(attempt - 1, 0), 10);
        long exponential = Math.min(maxDelayMs, baseDelayMs << shift);
        double factor = (1 - jitter) + jitter * random.nextDouble();
        return Math.max(1, (long) (exponential * factor));
    }
}
