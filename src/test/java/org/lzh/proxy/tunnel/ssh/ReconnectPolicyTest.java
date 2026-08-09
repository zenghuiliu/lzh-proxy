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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * 指数退避 + 抖动延迟计算单元测试。
 */
class ReconnectPolicyTest {

    @Test
    void firstAttemptIsBaseDelayWithinJitterRange() {
        long base = 1000;
        double jitter = 0.2;
        ReconnectPolicy policy = new ReconnectPolicy(base, 60_000, jitter, new Random(42));
        long delay = policy.nextDelayMs(1);
        assertThat(delay).isBetween(800L, 1000L); // base * (1-jitter) .. base
    }

    @Test
    void secondAttemptDoublesBase() {
        ReconnectPolicy policy = new ReconnectPolicy(1000, 60_000, 0.0, new Random(1));
        assertThat(policy.nextDelayMs(2)).isEqualTo(2000);
    }

    @Test
    void delayCappedAtMax() {
        ReconnectPolicy policy = new ReconnectPolicy(1000, 5000, 0.0, new Random(1));
        // attempt 4 -> base << 3 = 8000, 封顶到 5000
        assertThat(policy.nextDelayMs(4)).isEqualTo(5000);
        assertThat(policy.nextDelayMs(20)).isEqualTo(5000);
    }

    @Test
    void delayNeverZero() {
        ReconnectPolicy policy = new ReconnectPolicy(1, 1, 1.0, new Random(1));
        assertThat(policy.nextDelayMs(1)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void jitterDelayStaysWithinBounds() {
        long base = 2000;
        long max = 60_000;
        double jitter = 0.2;
        ReconnectPolicy policy = new ReconnectPolicy(base, max, jitter, new Random(7));
        for (int attempt = 1; attempt <= 20; attempt++) {
            long delay = policy.nextDelayMs(attempt);
            long raw = Math.min(max, base << Math.min(attempt - 1, 10));
            assertThat(delay).isBetween((long) (raw * (1 - jitter)), raw);
        }
    }
}
