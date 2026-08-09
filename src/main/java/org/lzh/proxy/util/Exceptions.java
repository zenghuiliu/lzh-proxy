package org.lzh.proxy.util;

/**
 * 轻量异常与资源关闭辅助，避免样板代码。
 */
public final class Exceptions {

    private Exceptions() {
    }

    /** 吞掉关闭异常（日志由调用方决定是否需要）。 */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 关闭失败无需上抛
            }
        }
    }

    /** 将受检异常包装为运行时异常。 */
    public static RuntimeException wrap(Throwable t) {
        if (t instanceof RuntimeException re) {
            return re;
        }
        return new RuntimeException(t);
    }
}
