package org.lzh.proxy.control;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.lzh.proxy.config.ProxyBinding;

/**
 * 注册报文编码/解码："appIp,appPort,remotePort\r\n"。
 */
public final class RegisterProtocol {

    private RegisterProtocol() {
    }

    /** 编码注册报文（客户端 -> 服务端）。 */
    public static byte[] encode(ProxyBinding binding) {
        String payload = binding.appIp() + "," + binding.appPort() + "," + binding.remotePort() + "\r\n";
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    /** 注册信息。 */
    public record Registration(String appIp, int appPort, int remotePort) {
    }

    /** 解析注册报文；格式非法返回空。 */
    public static Optional<Registration> parse(byte[] data) {
        if (data == null) {
            return Optional.empty();
        }
        String[] parts = new String(data, StandardCharsets.UTF_8).split(",");
        if (parts.length < 3) {
            return Optional.empty();
        }
        try {
            String ip = parts[0].trim();
            int appPort = Integer.parseInt(parts[1].trim());
            int remotePort = Integer.parseInt(parts[2].trim());
            if (ip.isEmpty() || appPort < 1 || appPort > 65535 || remotePort < 1 || remotePort > 65535) {
                return Optional.empty();
            }
            return Optional.of(new Registration(ip, appPort, remotePort));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
