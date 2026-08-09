package org.lzh.proxy.forward;

import java.util.Collection;
import java.util.function.Supplier;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * 控制通道写可控性 -> 叶子通道读暂停 的背压联动。
 *
 * <p>控制通道为多条隧道共享（不能按隧道暂停其读），因此当控制通道不可写
 * （对端消费慢）时，暂停所有叶子源通道（服务端用户通道 / 客户端 app 通道）的读，
 * 使数据生产停止；恢复可写时再恢复读。</p>
 *
 * <p>死锁规避：只暂停叶子通道，绝不暂停控制通道本身；每条通道关闭路径都会由
 * 隧道清理流程结束（关闭即无需恢复）。</p>
 */
public class FlowControlHandler extends ChannelDuplexHandler {

    private final Supplier<Collection<Channel>> leafChannels;
    private boolean paused;

    public FlowControlHandler(Supplier<Collection<Channel>> leafChannels) {
        this.leafChannels = leafChannels;
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean writable = ctx.channel().isWritable();
        if (writable == paused) {
            return;
        }
        paused = !writable;
        for (Channel leaf : leafChannels.get()) {
            if (leaf != null && leaf.isActive()) {
                leaf.config().setAutoRead(writable);
            }
        }
        super.channelWritabilityChanged(ctx);
    }
}
