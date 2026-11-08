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
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MailjetMailChannelTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> bodies = new ArrayList<>();
    private final List<String> auths = new ArrayList<>();
    private HttpServer server;

    private MailjetMailChannel channel(int status, String response) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3.1/send", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auths.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        var config = new MailDeliveryProperties.Mailjet("llave", "secreto", "http://127.0.0.1:" + server.getAddress().getPort(), 5);
        return new MailjetMailChannel(config, mapper, HttpClient.newHttpClient());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsMessageWithBasicAuthentication() throws Exception {
        MailjetMailChannel mailjet = channel(200, "{\"Messages\":[{\"Status\":\"success\",\"To\":[{\"Email\":\"ana@correo.com\"}]}]}");
        mailjet.send(new OutgoingMail("ana@correo.com", "Asunto", "Texto", "<p>Html</p>"), "remitente@correo.com", "AquaSense AI");
        assertThat(auths.get(0)).isEqualTo("Basic " + Base64.getEncoder().encodeToString("llave:secreto".getBytes(StandardCharsets.UTF_8)));
        JsonNode message = mapper.readTree(bodies.get(0)).path("Messages").get(0);
        assertThat(message.path("From").path("Email").asText()).isEqualTo("remitente@correo.com");
        assertThat(message.path("From").path("Name").asText()).isEqualTo("AquaSense AI");
        assertThat(message.path("To").get(0).path("Email").asText()).isEqualTo("ana@correo.com");
        assertThat(message.path("Subject").asText()).isEqualTo("Asunto");
        assertThat(message.path("TextPart").asText()).isEqualTo("Texto");
        assertThat(message.path("HTMLPart").asText()).isEqualTo("<p>Html</p>");
    }

    @Test
    void reportsMessageLevelAndAuthenticationErrors() throws Exception {
        MailjetMailChannel mailjet = channel(400, "{\"Messages\":[{\"Status\":\"error\",\"Errors\":[{\"ErrorMessage\":"
            + "\"The sender is not a validated sender\"}]}]}");
        assertThatThrownBy(() -> mailjet.send(new OutgoingMail("a@b.co", "s", "t", null), "x@y.co", "A"))
            .isInstanceOf(MailDeliveryException.class).hasMessageContaining("400").hasMessageContaining("not a validated sender");
        server.stop(0);
        MailjetMailChannel unauthorized = channel(401, "{\"ErrorIdentifier\":\"x\",\"ErrorMessage\":\"API key authentication/authorization failure\"}");
        assertThatThrownBy(() -> unauthorized.send(new OutgoingMail("a@b.co", "s", "t", null), "x@y.co", "A"))
            .hasMessageContaining("401").hasMessageContaining("authentication");
    }
}
