package com.aquasense.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Servidor HTTP local que imita el endpoint /chat/completions de una API compatible con OpenAI.
 * Solo para pruebas: devuelve respuestas programadas y guarda las solicitudes recibidas.
 */
public class FakeAiServer implements AutoCloseable {

    private final HttpServer server;
    private final Deque<int[]> statuses = new ArrayDeque<>();
    private final Deque<String> bodies = new ArrayDeque<>();
    private final Deque<String> retryAfter = new ArrayDeque<>();
    private final List<String> requests = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();
    private final List<String> userAgents = new ArrayList<>();

    public FakeAiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            int status = statuses.isEmpty() ? 200 : statuses.pollFirst()[0];
            String body = bodies.isEmpty() ? "{}" : bodies.pollFirst();
            String wait = retryAfter.isEmpty() ? "" : retryAfter.pollFirst();
            if (!wait.isEmpty()) {
                exchange.getResponseHeaders().add("Retry-After", wait);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    public FakeAiServer reply(int status, String body) {
        return reply(status, body, "");
    }

    public FakeAiServer reply(int status, String body, String retryAfterHeader) {
        statuses.add(new int[] {status});
        bodies.add(body);
        retryAfter.add(retryAfterHeader);
        return this;
    }

    public FakeAiServer replyContent(String model, String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return reply(200, "{\"model\":\"" + model + "\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
            + escaped + "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":80}}");
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    public List<String> requests() { return requests; }
    public List<String> authorizations() { return authorizations; }
    public List<String> userAgents() { return userAgents; }

    @Override
    public void close() {
        server.stop(0);
    }
}
