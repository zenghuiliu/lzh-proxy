package org.lzh.proxy.management;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 轻量运行指标注册表（无框架）。
 */
public class MetricsRegistry {

    private final LongAdder tunnelsOpened = new LongAdder();
    private final LongAdder tunnelsClosed = new LongAdder();
    private final LongAdder bytesS2c = new LongAdder();
    private final LongAdder bytesC2s = new LongAdder();
    private final LongAdder controlReconnects = new LongAdder();
    private final LongAdder sshReconnectAttempts = new LongAdder();
    private final LongAdder sshReconnectFailures = new LongAdder();
    private final LongAdder registerRejects = new LongAdder();
    private final AtomicLong activeTunnels = new AtomicLong();

    public void tunnelOpened() {
        tunnelsOpened.increment();
        activeTunnels.incrementAndGet();
    }

    public void tunnelClosed() {
        tunnelsClosed.increment();
        activeTunnels.decrementAndGet();
    }

    /** 服务端 -> 客户端方向字节。 */
    public void bytesS2c(long n) {
        bytesS2c.add(n);
    }

    /** 客户端 -> 服务端方向字节。 */
    public void bytesC2s(long n) {
        bytesC2s.add(n);
    }

    public void controlReconnect() {
        controlReconnects.increment();
    }

    public void sshReconnectAttempt() {
        sshReconnectAttempts.increment();
    }

    public void sshReconnectFailure() {
        sshReconnectFailures.increment();
    }

    public void registerReject() {
        registerRejects.increment();
    }

    /** 当前快照。 */
    public Metrics snapshot(long uptimeSeconds) {
        return new Metrics(
                tunnelsOpened.sum(),
                tunnelsClosed.sum(),
                activeTunnels.get(),
                bytesS2c.sum(),
                bytesC2s.sum(),
                controlReconnects.sum(),
                sshReconnectAttempts.sum(),
                sshReconnectFailures.sum(),
                registerRejects.sum(),
                uptimeSeconds);
    }
}
