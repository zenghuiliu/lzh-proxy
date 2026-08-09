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

import java.util.concurrent.atomic.AtomicLong;

/**
 * 隧道序列号生成器。
 *
 * <p>替代原 Main.serverChannelSerial：序列号用于在客户端/服务端间关联
 * 同一隧道的两条通道，保证唯一性即可。</p>
 */
public class SerialGenerator {

    private final AtomicLong next = new AtomicLong(0);

    public long next() {
        return next.getAndIncrement();
    }
}
