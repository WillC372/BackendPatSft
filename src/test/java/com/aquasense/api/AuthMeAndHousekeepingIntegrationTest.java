package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquasense.user.PasswordResetToken;
import com.aquasense.user.PasswordResetTokenRepository;
import com.aquasense.user.TokenCleanup;
import com.aquasense.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** GET /auth/me, the database-free liveness address and the cleanup of finished account links. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthMeAndHousekeepingIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordResetTokenRepository tokens;

    @Autowired
    private TokenCleanup cleanup;

    @Test
    void authMeReturnsTheAccountAndTheCurrentSession() throws Exception {
        String email = "yo-" + UUID.randomUUID() + "@aquasense.test";
        JsonNode auth = mapper.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Usuario Yo\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = auth.path("token").asText();

        JsonNode me = mapper.readTree(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(me.path("user").path("email").asText()).isEqualTo(email);
        assertThat(me.path("user").path("fullName").asText()).isEqualTo("Usuario Yo");
        assertThat(me.path("session").path("current").asBoolean()).isTrue();
        assertThat(me.path("session").path("status").asText()).isEqualTo("OPEN");
        assertThat(me.path("session").path("expiresAt").asText()).isNotBlank();

        JsonNode users = mapper.readTree(mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(users.path("email").asText()).isEqualTo(email);

        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void theLivenessAddressAnswersWithoutSigningIn() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void usedAndExpiredLinksAreDeletedAndOpenOnesAreKept() throws Exception {
        String email = "enlaces-" + UUID.randomUUID() + "@aquasense.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"Usuario Enlaces\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated());
        Long userId = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        Instant now = Instant.now();
        String expired = "h-" + UUID.randomUUID();
        String used = "h-" + UUID.randomUUID();
        String open = "h-" + UUID.randomUUID();
        String justExpired = "h-" + UUID.randomUUID();
        tokens.save(new PasswordResetToken(userId, expired, now.minus(Duration.ofDays(2))));
        PasswordResetToken usedToken = new PasswordResetToken(userId, used, now.plus(Duration.ofMinutes(30)));
        usedToken.markUsed(now.minus(Duration.ofDays(2)));
        tokens.save(usedToken);
        tokens.save(new PasswordResetToken(userId, open, now.plus(Duration.ofMinutes(30))));
        tokens.save(new PasswordResetToken(userId, justExpired, now.minus(Duration.ofHours(2))));

        assertThat(cleanup.deleteFinishedTokens()).isGreaterThanOrEqualTo(2);
        assertThat(find(expired)).isEmpty();
        assertThat(find(used)).isEmpty();
        assertThat(find(open)).isPresent();
        assertThat(find(justExpired)).isPresent();
    }

    private java.util.Optional<PasswordResetToken> find(String hash) {
        return tokens.findByTokenHashAndPurpose(hash, PasswordResetToken.Purpose.PASSWORD_RESET);
    }
}
