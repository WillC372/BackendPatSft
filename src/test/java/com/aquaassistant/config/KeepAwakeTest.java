package com.aquaassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class KeepAwakeTest {

    @Test
    void withoutAPublicAddressItDoesNothing() {
        KeepAwake keepAwake = new KeepAwake("", HttpClient.newHttpClient());
        assertThat(keepAwake.enabled()).isFalse();
        keepAwake.ping(); // no exception, no request
    }

    @Test
    void asksTheLivenessProbeOfItsOwnAddress() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger hits = new AtomicInteger();
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            path.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            KeepAwake keepAwake = new KeepAwake("http://127.0.0.1:" + server.getAddress().getPort() + "/",
                HttpClient.newHttpClient());
            assertThat(keepAwake.target()).endsWith(":" + server.getAddress().getPort() + "/actuator/health/liveness");
            keepAwake.ping();
            keepAwake.ping();
            assertThat(hits.get()).isEqualTo(2);
            assertThat(path.get()).isEqualTo("/actuator/health/liveness");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aServerThatDoesNotAnswerDoesNotBreakTheSchedule() {
        KeepAwake keepAwake = new KeepAwake("http://127.0.0.1:9", HttpClient.newHttpClient());
        keepAwake.ping();
        keepAwake.ping();
        assertThat(keepAwake.enabled()).isTrue();
    }
}
