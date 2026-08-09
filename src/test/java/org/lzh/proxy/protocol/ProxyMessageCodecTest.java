package org.lzh.proxy.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.lzh.proxy.config.Constants;

/**
 * 协议编解码测试：golden-bytes 锁定线格式不变；畸形帧不崩溃。
 */
class ProxyMessageCodecTest {

    private static final int SERIAL_LEN = 8;

    private EmbeddedChannel newChannel() {
        return new EmbeddedChannel(
                new ProxyMessageDecoder(Constants.MAX_FRAME_LENGTH, Constants.LENGTH_FIELD_OFFSET,
                        Constants.LENGTH_FIELD_LENGTH, Constants.LENGTH_ADJUSTMENT,
                        Constants.INITIAL_BYTES_TO_STRIP),
                new ProxyMessageEncoder());
    }

    /** 构造旧版本编码器应输出的 golden 字节：长度(4) + type(1) + serial(8) + data。 */
    private static byte[] golden(byte type, long serial, byte[] data) {
        int len = 1 + SERIAL_LEN + (data == null ? 0 : data.length);
        ByteBuf buf = Unpooled.buffer(4 + len);
        buf.writeInt(len);
        buf.writeByte(type);
        buf.writeLong(serial);
        if (data != null) {
            buf.writeBytes(data);
        }
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        buf.release();
        return out;
    }

    @Test
    void connectFrameIsByteForByteCompatible() {
        long serial = 123L;
        EmbeddedChannel ch = newChannel();
        ch.writeOutbound(ProxyMessage.connect(serial));
        ByteBuf out = ch.readOutbound();
        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);
        out.release();
        assertThat(actual).isEqualTo(golden(Constants.TYPE_CONNECT, serial, null));
    }

    @Test
    void transferFrameIsByteForByteCompatible() {
        long serial = 42L;
        byte[] data = "hello-lzh-proxy".getBytes();
        EmbeddedChannel ch = newChannel();
        ch.writeOutbound(ProxyMessage.transfer(serial, data));
        ByteBuf out = ch.readOutbound();
        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);
        out.release();
        assertThat(actual).isEqualTo(golden(Constants.TYPE_TRANSFER, serial, data));
    }

    @Test
    void pingAndPongUseSentinelSerial() {
        EmbeddedChannel ch = newChannel();
        ch.writeOutbound(ProxyMessage.ping());
        ByteBuf out = ch.readOutbound();
        byte[] actual = new byte[out.readableBytes()];
        out.readBytes(actual);
        out.release();
        assertThat(actual).isEqualTo(golden(Constants.TYPE_HEART_BEET_PING, -1L, null));
    }

    @Test
    void encodeDecodeRoundTrip() {
        EmbeddedChannel ch = newChannel();
        ProxyMessage msg = ProxyMessage.transfer(7L, "payload-bytes".getBytes());
        ch.writeOutbound(msg);
        ByteBuf bytes = ch.readOutbound();
        assertThat(ch.writeInbound(bytes)).isTrue();
        ProxyMessage decoded = ch.readInbound();
        assertThat(decoded.type()).isEqualTo(msg.type());
        assertThat(decoded.serial()).isEqualTo(msg.serial());
        assertThat(decoded.data()).isEqualTo(msg.data());
    }

    @Test
    void emptyTransferRoundTrip() {
        EmbeddedChannel ch = newChannel();
        ProxyMessage msg = ProxyMessage.transfer(9L, new byte[0]);
        ch.writeOutbound(msg);
        ByteBuf bytes = ch.readOutbound();
        assertThat(ch.writeInbound(bytes)).isTrue();
        ProxyMessage decoded = ch.readInbound();
        assertThat(decoded.data()).isEmpty();
        assertThat(decoded.serial()).isEqualTo(9L);
    }

    @Test
    void malformedTinyFrameRejectedNotCrash() {
        // 长度字段声明 4 字节，但帧只有 type+serial(9) 实际足够——构造更小的：声明长度 2
        ByteBuf in = Unpooled.buffer();
        in.writeInt(2);          // 声称 body 只有 2 字节
        in.writeByte(Constants.TYPE_TRANSFER);
        in.writeByte(0x00);
        in.writeLong(1L);        // 实际多余字节
        in.writeByte(0x00);
        EmbeddedChannel ch = newChannel();
        // 最小帧校验会抛 DecoderException，绝不抛 NegativeArraySizeException
        assertThatThrownBy(() -> ch.writeInbound(in))
                .isInstanceOf(DecoderException.class)
                .hasMessageContaining("frame too small");
    }

    @Test
    void oversizedFrameRejected() {
        EmbeddedChannel ch = newChannel();
        ByteBuf in = Unpooled.buffer();
        in.writeInt(Constants.MAX_FRAME_LENGTH + 1); // 超过 1MB 上限
        assertThatThrownBy(() -> ch.writeInbound(in))
                .isInstanceOf(DecoderException.class);
    }
}
