package com.aquasense.user.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BrevoMailChannelTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> bodies = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private HttpServer server;

    private BrevoMailChannel channel(int status, String response) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(exchange.getRequestHeaders().getFirst("api-key"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        var config = new MailDeliveryProperties.Brevo("xkeysib-prueba", "http://127.0.0.1:" + server.getAddress().getPort() + "/v3", 5);
        return new BrevoMailChannel(config, mapper, HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsTransactionalEmailWithApiKeyHeader() throws Exception {
        BrevoMailChannel brevo = channel(201, "{\"messageId\":\"<abc@smtp-relay.mailin.fr>\"}");
        assertThat(brevo.isConfigured()).isTrue();
        brevo.send(new OutgoingMail("ana@correo.com", "Asunto", "Texto", "<p>Html</p>"), "remitente@correo.com", "AquaSense AI");
        assertThat(keys).containsExactly("xkeysib-prueba");
        JsonNode body = mapper.readTree(bodies.get(0));
        assertThat(body.path("sender").path("email").asText()).isEqualTo("remitente@correo.com");
        assertThat(body.path("sender").path("name").asText()).isEqualTo("AquaSense AI");
        assertThat(body.path("to").get(0).path("email").asText()).isEqualTo("ana@correo.com");
        assertThat(body.path("subject").asText()).isEqualTo("Asunto");
        assertThat(body.path("textContent").asText()).isEqualTo("Texto");
        assertThat(body.path("htmlContent").asText()).isEqualTo("<p>Html</p>");
    }

    @Test
    void reportsBrevoErrorMessage() throws Exception {
        BrevoMailChannel brevo = channel(400, "{\"code\":\"invalid_parameter\",\"message\":\"Sender is not valid\"}");
        assertThatThrownBy(() -> brevo.send(new OutgoingMail("a@b.co", "s", "t", null), "x@y.co", "A"))
            .isInstanceOf(MailDeliveryException.class).hasMessageContaining("400").hasMessageContaining("Sender is not valid");
    }
}
