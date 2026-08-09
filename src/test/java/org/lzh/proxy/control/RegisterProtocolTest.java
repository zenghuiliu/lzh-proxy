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
