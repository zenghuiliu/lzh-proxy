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
