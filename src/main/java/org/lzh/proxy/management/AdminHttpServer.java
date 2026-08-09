package org.lzh.proxy.management;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.lzh.proxy.config.AppConfig;
import org.lzh.proxy.forward.TunnelRegistry;
import org.lzh.proxy.lifecycle.Lifecycle;
import org.lzh.proxy.tunnel.ssh.SshSessionManager;
import org.lzh.proxy.tunnel.ssh.SshState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 轻量管理 HTTP 服务（仅回环，默认关闭）。
 *
 * <ul>
 *   <li>GET /healthz  200 ok / 503 stopping</li>
 *   <li>GET /metrics  text/plain key value</li>
 *   <li>GET /status   角色/注册地址/隧道数/SSH 状态（JSON 风格）</li>
 * </ul>
 */
public class AdminHttpServer implements Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(AdminHttpServer.class);

    private final AppConfig config;
    private final MetricsRegistry metrics;
    private final TunnelRegistry tunnelRegistry;
    private final SshSessionManager sshManager;
    private final long startedAtMs;

    private volatile HttpServer server;
    private volatile boolean stopping;

    public AdminHttpServer(AppConfig config, MetricsRegistry metrics, TunnelRegistry tunnelRegistry,
                           SshSessionManager sshManager) {
        this.config = config;
        this.metrics = metrics;
        this.tunnelRegistry = tunnelRegistry;
        this.sshManager = sshManager;
        this.startedAtMs = System.currentTimeMillis();
    }

    @Override
    public void start() throws IOException {
        if (!config.management().enabled()) {
            return;
        }
        HttpServer httpServer = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), config.management().port()), 0);
        httpServer.createContext("/healthz", this::handleHealthz);
        httpServer.createContext("/metrics", this::handleMetrics);
        httpServer.createContext("/status", this::handleStatus);
        httpServer.start();
        this.server = httpServer;
        log.info("management server started on {}", httpServer.getAddress());
    }

    @Override
    public void stop() {
        stopping = true;
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handleHealthz(HttpExchange exchange) throws IOException {
        respond(exchange, stopping ? 503 : 200, stopping ? "stopping" : "ok");
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        long uptime = (System.currentTimeMillis() - startedAtMs) / 1000;
        Metrics m = metrics.snapshot(uptime);
        StringBuilder sb = new StringBuilder(512);
        sb.append("role ").append(config.role()).append('\n');
        sb.append("uptime_seconds ").append(m.uptimeSeconds()).append('\n');
        sb.append("tunnels_opened ").append(m.tunnelsOpened()).append('\n');
        sb.append("tunnels_closed ").append(m.tunnelsClosed()).append('\n');
        sb.append("tunnels_active ").append(m.activeTunnels()).append('\n');
        sb.append("bytes_server_to_client ").append(m.bytesS2c()).append('\n');
        sb.append("bytes_client_to_server ").append(m.bytesC2s()).append('\n');
        sb.append("control_reconnects ").append(m.controlReconnects()).append('\n');
        sb.append("ssh_reconnect_attempts ").append(m.sshReconnectAttempts()).append('\n');
        sb.append("ssh_reconnect_failures ").append(m.sshReconnectFailures()).append('\n');
        sb.append("register_rejects ").append(m.registerRejects()).append('\n');
        respond(exchange, 200, sb.toString());
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        long uptime = (System.currentTimeMillis() - startedAtMs) / 1000;
        Metrics m = metrics.snapshot(uptime);
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\n");
        sb.append("  \"role\": \"").append(config.role()).append("\",\n");
        sb.append("  \"register\": \"").append(config.register().ip()).append(':')
                .append(config.register().port()).append("\",\n");
        sb.append("  \"uptime_seconds\": ").append(m.uptimeSeconds()).append(",\n");
        sb.append("  \"active_tunnels\": ").append(m.activeTunnels()).append(",\n");
        sb.append("  \"channel_maps\": {");
        Map<String, Integer> sizes = tunnelRegistry.snapshotSizes();
        boolean first = true;
        for (Map.Entry<String, Integer> e : sizes.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\": ").append(e.getValue());
        }
        sb.append("},\n");
        sb.append("  \"ssh\": {");
        if (sshManager != null) {
            Map<String, SshState> states = sshManager.snapshotStates();
            boolean firstSsh = true;
            for (Map.Entry<String, SshState> e : states.entrySet()) {
                if (!firstSsh) {
                    sb.append(", ");
                }
                firstSsh = false;
                sb.append('"').append(e.getKey()).append("\": \"").append(e.getValue()).append('"');
            }
        }
        sb.append("}\n");
        sb.append("}\n");
        respond(exchange, 200, sb.toString());
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
