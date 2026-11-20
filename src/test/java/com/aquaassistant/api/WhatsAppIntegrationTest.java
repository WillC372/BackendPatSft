package com.aquaassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquaassistant.notify.Notifier;
import com.aquaassistant.notify.WhatsAppService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * H-058: the mobile number of the account and the pH alarms by WhatsApp. A local server stands in for the WhatsApp
 * Cloud API of Meta and keeps what it receives.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WhatsAppIntegrationTest {

    record Received(String path, String authorization, String body) {
    }

    static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    static final HttpServer META = start();

    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                RECEIVED.add(new Received(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                byte[] answer = "{\"messages\":[{\"id\":\"wamid.test\"}]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, answer.length);
                exchange.getResponseBody().write(answer);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void whatsApp(DynamicPropertyRegistry registry) {
        registry.add("aquasense.whatsapp.token", () -> "token-de-prueba");
        registry.add("aquasense.whatsapp.phone-number-id", () -> "123456789");
        registry.add("aquasense.whatsapp.api-url", () -> "http://127.0.0.1:" + META.getAddress().getPort() + "/v21.0");
    }

    @AfterAll
    static void stop() {
        META.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void theNumberIsSavedInInternationalFormatAndATestMessageArrives() throws Exception {
        String token = register();
        mvc.perform(auth(get("/api/v1/users/me/whatsapp"), token)).andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.phoneNumber").doesNotExist());
        mvc.perform(auth(put("/api/v1/users/me/whatsapp"), token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"12345\"}")).andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/v1/users/me/whatsapp"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"300 123 4567\",\"enabled\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phoneNumber").value("+573001234567"));

        int before = RECEIVED.size();
        mvc.perform(auth(post("/api/v1/users/me/whatsapp/test"), token)).andExpect(jsonPath("$.sent").value(true));
        Received message = RECEIVED.get(before);
        assertThat(message.path()).isEqualTo("/v21.0/123456789/messages");
        assertThat(message.authorization()).isEqualTo("Bearer token-de-prueba");
        JsonNode body = mapper.readTree(message.body());
        assertThat(body.path("to").asText()).isEqualTo("573001234567");
        assertThat(body.path("template").path("name").asText()).isEqualTo("alerta_ph");
        assertThat(body.path("template").path("language").path("code").asText()).isEqualTo("es");
    }

    @Test
    void anAlterationOfThePhGoesByWhatsAppButAnotherAlertDoesNot() throws Exception {
        String token = register();
        mvc.perform(auth(put("/api/v1/users/me/whatsapp"), token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"+573109998877\"}")).andExpect(status().isOk());
        measure(token, 7.0);
        int before = RECEIVED.size();
        measure(token, 12.5);
        long until = System.currentTimeMillis() + 5000;
        while (RECEIVED.size() == before && System.currentTimeMillis() < until) {
            Thread.sleep(50);
        }
        assertThat(RECEIVED.size()).isEqualTo(before + 1);
        JsonNode body = mapper.readTree(RECEIVED.get(before).body());
        assertThat(body.path("to").asText()).isEqualTo("573109998877");
        JsonNode parameters = body.path("template").path("components").get(0).path("parameters");
        assertThat(parameters.get(0).path("text").asText()).isEqualTo("Alteración del pH en el agua");
        assertThat(parameters.get(1).path("text").asText()).contains("subió de 7.00 a 12.50").contains("Es peligroso");
    }

    @Test
    void numbersAndAddresses() {
        assertThat(WhatsAppService.normalize("+57 (300) 123-4567")).isEqualTo("+573001234567");
        assertThat(WhatsAppService.normalize("0034 612 345 678")).isEqualTo("+34612345678");
        assertThat(WhatsAppService.normalize("  ")).isNull();
        assertThatThrownBy(() -> WhatsAppService.normalize("3001234")).isInstanceOf(IllegalArgumentException.class);
        // The made-up addresses of the old demo accounts and the example ones receive no email; a real mailbox does.
        assertThat(Notifier.deliverable("premium@demo.aquaassistant")).isFalse();
        assertThat(Notifier.deliverable("docente.aqua@example.com")).isFalse();
        assertThat(Notifier.deliverable("aqua.usuario.demo@gmail.com")).isTrue();
    }

    private void measure(String token, double ph) throws Exception {
        mvc.perform(auth(post("/api/v1/measurements"), token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"ph\":" + ph + "}")).andExpect(status().isCreated());
    }

    private String register() throws Exception {
        String email = "w" + UUID.randomUUID().toString().substring(0, 8) + "@aquasense.test";
        return mapper.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Persona WhatsApp\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("token").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
