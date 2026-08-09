package org.lzh.proxy.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.lzh.proxy.config.ProxyBinding;

/**
 * 注册报文编解码单元测试。
 */
class RegisterProtocolTest {

    @Test
    void encodesAndParsesRoundTrip() {
        ProxyBinding binding = new ProxyBinding("127.0.0.1", 7002, 7001);
        byte[] encoded = RegisterProtocol.encode(binding);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo("127.0.0.1,7002,7001\r\n");
        RegisterProtocol.Registration reg = RegisterProtocol.parse(encoded).orElseThrow();
        assertThat(reg.appIp()).isEqualTo("127.0.0.1");
        assertThat(reg.appPort()).isEqualTo(7002);
        assertThat(reg.remotePort()).isEqualTo(7001);
    }

    @Test
    void rejectsTooFewParts() {
        assertThat(RegisterProtocol.parse("127.0.0.1,7002\r\n".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void rejectsNonNumericPort() {
        assertThat(RegisterProtocol.parse("127.0.0.1,abc,7001\r\n".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void rejectsOutOfRangePort() {
        assertThat(RegisterProtocol.parse("127.0.0.1,99999,7001\r\n".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(RegisterProtocol.parse(null)).isEmpty();
    }
}
