package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Recuperación de contraseña completa con un servidor local que imita la API de Mailjet: el correo llega con el enlace,
 * el enlace cambia la contraseña una sola vez y los correos no registrados no generan envío.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordRecoveryEmailIntegrationTest {

    private static final List<String> SENT = new CopyOnWriteArrayList<>();
    private static final HttpServer MAILJET;

    static {
        try {
            MAILJET = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        MAILJET.createContext("/v3.1/send", exchange -> {
            SENT.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = "{\"Messages\":[{\"Status\":\"success\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        MAILJET.start();
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("aquasense.security.admin-email", () -> "admin-mail@aquasense.test");
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
    void resetLinkArrivesByEmailAndWorksOnce() throws Exception {
        String email = "r" + UUID.randomUUID().toString().substring(0, 8) + "@aquasense.test";
        send("/api/v1/auth/register", "{\"fullName\":\"Prueba\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}");
        int before = SENT.size();

        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nadie-" + UUID.randomUUID() + "@aquasense.test\"}")).andExpect(status().isOk());
        assertThat(SENT).hasSize(before);

        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email.toUpperCase() + "\"}")).andExpect(status().isOk());
        assertThat(SENT).hasSize(before + 1);
        JsonNode sent = mapper.readTree(SENT.get(before)).path("Messages").get(0);
        assertThat(sent.path("To").get(0).path("Email").asText()).isEqualTo(email);
        assertThat(sent.path("Subject").asText()).contains("Restablecer contraseña");
        Matcher m = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)").matcher(sent.path("TextPart").asText());
        assertThat(m.find()).isTrue();
        String token = m.group(1);
        assertThat(sent.path("HTMLPart").asText()).contains(token);

        // Un segundo enlace pedido antes del cambio queda inválido cuando se usa el primero.
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
        Matcher second = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)")
            .matcher(mapper.readTree(SENT.get(SENT.size() - 1)).path("Messages").get(0).path("TextPart").asText());
        assertThat(second.find()).isTrue();
        String reset = "{\"token\":\"" + token + "\",\"newPassword\":\"NuevaClave9\"}";
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(reset))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(reset))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + second.group(1) + "\",\"newPassword\":\"OtraClave77\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"NuevaClave9\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"Clave1234\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoleRequiresConfirmationFromTheAdminMailbox() throws Exception {
        String adminEmail = "admin-mail@aquasense.test";
        int before = SENT.size();
        MvcResult registered = send("/api/v1/auth/register",
            "{\"fullName\":\"Admin\",\"email\":\"" + adminEmail + "\",\"password\":\"Clave1234\"}");
        assertThat(json(registered).path("user").path("role").asText()).isEqualTo("USER");
        assertThat(json(registered).path("notice").asText()).contains("administrador");
        String unconfirmed = token(registered);
        mvc.perform(get("/api/v1/admin/mail").header("Authorization", "Bearer " + unconfirmed)).andExpect(status().isForbidden());

        assertThat(SENT).hasSize(before + 1);
        JsonNode mail = mapper.readTree(SENT.get(before)).path("Messages").get(0);
        assertThat(mail.path("To").get(0).path("Email").asText()).isEqualTo(adminEmail);
        assertThat(mail.path("Subject").asText()).contains("administrador");
        Matcher m = Pattern.compile("verify-admin\\?token=([A-Za-z0-9_-]+)").matcher(mail.path("TextPart").asText());
        assertThat(m.find()).isTrue();

        // Un segundo inicio de sesión mientras el enlace sigue vigente no envía otro correo.
        send("/api/v1/auth/login", "{\"email\":\"" + adminEmail + "\",\"password\":\"Clave1234\"}");
        assertThat(SENT).hasSize(before + 1);

        String body = "{\"token\":\"" + m.group(1) + "\"}";
        mvc.perform(post("/api/v1/auth/verify-admin").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/verify-admin").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + unconfirmed)).andExpect(status().isUnauthorized());

        MvcResult login = send("/api/v1/auth/login", "{\"email\":\"" + adminEmail + "\",\"password\":\"Clave1234\"}");
        assertThat(json(login).path("user").path("role").asText()).isEqualTo("ADMIN");
        assertThat(json(login).path("notice").isNull() || json(login).path("notice").isMissingNode()).isTrue();
        String admin = token(login);
        JsonNode status = json(mvc.perform(get("/api/v1/admin/mail").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andReturn());
        assertThat(status.path("configured").asBoolean()).isTrue();
        assertThat(status.path("channel").asText()).contains("Mailjet");
        int sentBefore = SENT.size();
        JsonNode test = json(mvc.perform(post("/api/v1/admin/mail/test").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andReturn());
        assertThat(test.path("to").asText()).isEqualTo(adminEmail);
        assertThat(mapper.readTree(SENT.get(sentBefore)).path("Messages").get(0).path("To").get(0).path("Email").asText())
            .isEqualTo(adminEmail);

        String user = token(send("/api/v1/auth/register", "{\"fullName\":\"U\",\"email\":\"u" + UUID.randomUUID().toString()
            .substring(0, 8) + "@aquasense.test\",\"password\":\"Clave1234\"}"));
        mvc.perform(post("/api/v1/admin/mail/test").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
    }

    private MvcResult send(String path, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private String token(MvcResult result) throws Exception {
        return json(result).path("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
