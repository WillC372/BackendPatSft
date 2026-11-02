package com.aquasense.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquasense.identity.service.PasswordResetNotifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    static final List<String> SENT_CODES = new ArrayList<>();

    @TestConfiguration
    static class CapturingNotifierConfig {
        @Bean
        @Primary
        PasswordResetNotifier capturingNotifier() {
            return (email, name, code, minutes) -> SENT_CODES.add(code);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from users");
        SENT_CODES.clear();
    }

    private String json(Object... kv) throws Exception {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return mapper.writeValueAsString(map);
    }

    private void register(String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("displayName", "Ana", "email", email, "password", password,
                        "confirmPassword", password)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String identifier, String password, boolean remember) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("identifier", identifier, "password", password, "rememberMe", remember)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void registrationStoresArgon2idHashNeverPlaintext() throws Exception {
        register("ana@example.com", "Clave1234");
        String hash = jdbc.queryForObject("select password_hash from users", String.class);
        assertThat(hash).startsWith("$argon2id$").doesNotContain("Clave1234");
    }

    @Test
    void rejectsWeakPasswordMismatchAndDuplicates() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("displayName", "Ana", "email", "ana@example.com", "password", "short",
                        "confirmPassword", "short")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("displayName", "Ana", "email", "ana@example.com", "password", "Clave1234",
                        "confirmPassword", "Otra12345")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        register("ana@example.com", "Clave1234");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("displayName", "Otra", "email", "ANA@example.com", "password", "Clave1234",
                        "confirmPassword", "Clave1234")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void loginAccessProtectedResourceRefreshRotationAndLogout() throws Exception {
        register("ana@example.com", "Clave1234");
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());

        JsonNode tokens = login("ana@example.com", "Clave1234", false);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("ana@example.com"));

        String refresh = tokens.get("refreshToken").asText();
        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", refresh))).andExpect(status().isOk()).andReturn();
        String newRefresh = mapper.readTree(rotated.getResponse().getContentAsString()).get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", refresh))).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", newRefresh))).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", newRefresh))).andExpect(status().isUnauthorized());
    }

    @Test
    void rememberMeExtendsRefreshTokenLifetime() throws Exception {
        register("ana@example.com", "Clave1234");
        login("ana@example.com", "Clave1234", false);
        login("ana@example.com", "Clave1234", true);
        List<Long> days = jdbc.queryForList(
                "select round(extract(epoch from (expires_at - created_at)) / 86400)::bigint from refresh_tokens order by expires_at",
                Long.class);
        assertThat(days).containsExactly(7L, 30L);
    }

    @Test
    void locksAccountAfterRepeatedFailures() throws Exception {
        register("ana@example.com", "Clave1234");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json("identifier", "ana@example.com", "password", "mala-clave-1", "rememberMe", false)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("identifier", "ana@example.com", "password", "Clave1234", "rememberMe", false)))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void passwordResetFlowIsSingleUseAndInvalidatesSessions() throws Exception {
        register("ana@example.com", "Clave1234");
        String oldRefresh = login("ana@example.com", "Clave1234", false).get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", "nadie@example.com"))).andExpect(status().isOk());
        assertThat(SENT_CODES).isEmpty();

        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(json("email", "ana@example.com"))).andExpect(status().isOk());
        assertThat(SENT_CODES).hasSize(1);
        String code = SENT_CODES.get(0);

        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(json("code", code, "newPassword", "NuevaClave99", "confirmPassword", "NuevaClave99")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(json("code", code, "newPassword", "OtraClave99", "confirmPassword", "OtraClave99")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_CODE"));

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json("refreshToken", oldRefresh))).andExpect(status().isUnauthorized());
        login("ana@example.com", "NuevaClave99", false);
    }

    @Test
    void loginAcceptsUsername() throws Exception {
        String username = "ana_" + UUID.randomUUID().toString().substring(0, 6);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("displayName", "Ana", "email", "ana@example.com", "username", username,
                        "password", "Clave1234", "confirmPassword", "Clave1234")))
                .andExpect(status().isCreated());
        login(username.toUpperCase(), "Clave1234", false);
    }
}
