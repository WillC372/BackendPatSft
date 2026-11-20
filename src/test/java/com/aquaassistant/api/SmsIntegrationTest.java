package com.aquaassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.aquaassistant.notify.Notifier;
import com.aquaassistant.notify.SmsService;
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
import java.net.URLDecoder;

/**
 * H-067: the mobile number of the account and the pH alarm by SMS. A local server stands in for the Twilio API and
 * keeps what it receives; no real message is sent.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SmsIntegrationTest {

    record Received(String path, String authorization, String body) {
        String field(String name) {
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv[0].equals(name)) {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }

    static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    static final HttpServer TWILIO = start();

    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                RECEIVED.add(new Received(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                byte[] answer = "{\"sid\":\"SMtest\",\"status\":\"queued\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(201, answer.length);
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
    static void twilio(DynamicPropertyRegistry registry) {
        registry.add("aquasense.sms.twilio.account-sid", () -> "ACprueba");
        registry.add("aquasense.sms.twilio.auth-token", () -> "token-de-prueba");
        registry.add("aquasense.sms.twilio.from-number", () -> "+15005550006");
        registry.add("aquasense.sms.twilio.api-url", () -> "http://127.0.0.1:" + TWILIO.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        TWILIO.stop(0);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void theNumberIsSavedInInternationalFormatAndATestSmsArrives() throws Exception {
        String token = register();
        mvc.perform(auth(get("/api/v1/users/me/phone"), token)).andExpect(jsonPath("$.smsConfigured").value(true))
            .andExpect(jsonPath("$.phoneNumber").doesNotExist()).andExpect(jsonPath("$.smsAlerts").value(true));
        mvc.perform(auth(post("/api/v1/users/me/phone/test"), token)).andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/v1/users/me/phone"), token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"12345\"}")).andExpect(status().isBadRequest());
        mvc.perform(auth(put("/api/v1/users/me/phone"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"300 123 4567\",\"smsAlerts\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phoneNumber").value("+573001234567"));

        int before = RECEIVED.size();
        mvc.perform(auth(post("/api/v1/users/me/phone/test"), token)).andExpect(jsonPath("$.sent").value(true));
        Received message = RECEIVED.get(before);
        assertThat(message.path()).isEqualTo("/2010-04-01/Accounts/ACprueba/Messages.json");
        assertThat(message.authorization()).isEqualTo("Basic " + java.util.Base64.getEncoder()
            .encodeToString("ACprueba:token-de-prueba".getBytes(StandardCharsets.UTF_8)));
        assertThat(message.field("To")).isEqualTo("+573001234567");
        assertThat(message.field("From")).isEqualTo("+15005550006");
        assertThat(message.field("Body")).startsWith("Aqua Assistant AI");
    }

    @Test
    void anAlterationOfThePhGoesBySmsButNotWhenThePersonTurnsItOff() throws Exception {
        String token = register();
        mvc.perform(auth(put("/api/v1/users/me/phone"), token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"+573109998877\"}")).andExpect(status().isOk());
        measure(token, 7.0);
        int before = RECEIVED.size();
        measure(token, 12.5);
        long until = System.currentTimeMillis() + 5000;
        while (RECEIVED.size() == before && System.currentTimeMillis() < until) {
            Thread.sleep(50);
        }
        assertThat(RECEIVED.size()).isEqualTo(before + 1);
        Received sms = RECEIVED.get(before);
        assertThat(sms.field("To")).isEqualTo("+573109998877");
        assertThat(sms.field("Body")).contains("Alteración del pH en el agua").contains("subió de 7.00 a 12.50");
        assertThat(sms.field("Body").length()).isLessThanOrEqualTo(320);

        String other = register();
        mvc.perform(auth(put("/api/v1/users/me/phone"), other).contentType(MediaType.APPLICATION_JSON)
            .content("{\"phoneNumber\":\"+573109998866\",\"smsAlerts\":false}")).andExpect(status().isOk());
        measure(other, 7.0);
        int quiet = RECEIVED.size();
        measure(other, 12.5);
        Thread.sleep(1500);
        assertThat(RECEIVED.subList(quiet, RECEIVED.size())).noneMatch(r -> "+573109998866".equals(r.field("To")));
    }

    @Test
    void numbersAndTwilioErrors() {
        assertThat(SmsService.normalize("+57 (300) 123-4567")).isEqualTo("+573001234567");
        assertThat(SmsService.normalize("0034 612 345 678")).isEqualTo("+34612345678");
        assertThat(SmsService.normalize("  ")).isNull();
        assertThatThrownBy(() -> SmsService.normalize("3001234")).isInstanceOf(IllegalArgumentException.class);
        assertThat(SmsService.hint("+573001234567")).isEqualTo("+573***67");
    }

    private void measure(String token, double ph) throws Exception {
        mvc.perform(auth(post("/api/v1/measurements"), token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"ph\":" + ph + "}")).andExpect(status().isCreated());
    }

    private String register() throws Exception {
        String email = "s" + UUID.randomUUID().toString().substring(0, 8) + "@aquasense.test";
        return mapper.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Persona SMS\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("token").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
