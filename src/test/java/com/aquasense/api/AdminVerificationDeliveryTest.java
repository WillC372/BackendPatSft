package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * When the email with the administrator link cannot be delivered, the user is told so (instead of being told to check
 * a mailbox that will receive nothing) and the next login sends a new link.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminVerificationDeliveryTest {

    private static final String ADMIN = "admin-delivery@aquasense.test";
    private static final List<String> SENT = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean REJECT = new AtomicBoolean(true);
    private static final HttpServer MAILJET;

    static {
        try {
            MAILJET = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        MAILJET.createContext("/v3.1/send", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean reject = REJECT.get();
            if (!reject) {
                SENT.add(body);
            }
            byte[] bytes = (reject
                ? "{\"Messages\":[{\"Status\":\"error\",\"Errors\":[{\"ErrorMessage\":\"Sender is not authorized\"}]}]}"
                : "{\"Messages\":[{\"Status\":\"success\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reject ? 401 : 200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        MAILJET.start();
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("aquasense.security.admin-email", () -> ADMIN);
        registry.add("aquasense.mail-delivery.mailjet.api-key", () -> "llave-prueba");
        registry.add("aquasense.mail-delivery.mailjet.secret-key", () -> "secreto-prueba");
        registry.add("aquasense.mail-delivery.mailjet.base-url", () -> "http://127.0.0.1:" + MAILJET.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        MAILJET.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void failedDeliveryIsReportedAndTheNextLoginRetries() throws Exception {
        String registered = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Admin\",\"email\":\"" + ADMIN + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mapper.readTree(registered).path("notice").asText()).contains("No pudimos enviar");
        assertThat(SENT).isEmpty();

        REJECT.set(false);
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mapper.readTree(login).path("notice").asText()).contains("abre el enlace");
        assertThat(SENT).hasSize(1);

        // A third login right away does not send the email again.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + ADMIN + "\",\"password\":\"Clave1234\"}")).andExpect(status().isOk());
        assertThat(SENT).hasSize(1);
    }
}
