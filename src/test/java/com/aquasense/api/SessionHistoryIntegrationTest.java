package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Each login is a separate entry of the history, with the actions done in it, closed by logout. */
@SpringBootTest
@AutoConfigureMockMvc
class SessionHistoryIntegrationTest {

    private static final String PASSWORD = "Clave1234";
    private static final String CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void eachLoginIsANewHistoryEntryWithWhatWasDoneInIt() throws Exception {
        String email = "sesiones-" + UUID.randomUUID() + "@aquasense.test";
        String first = token(mvc.perform(post("/api/v1/auth/register").header("User-Agent", CHROME_WINDOWS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Usuario Sesiones\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isCreated()).andReturn());
        measure(first, 7.1);
        measure(first, 6.4);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + first)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + first)).andExpect(status().isUnauthorized());

        String second = login(email);
        measure(second, 7.4);

        JsonNode page = json(mvc.perform(get("/api/v1/users/me/sessions").header("Authorization", "Bearer " + second))
            .andExpect(status().isOk()).andReturn());
        assertThat(page.path("total").asLong()).isEqualTo(2);
        JsonNode current = page.path("items").get(0);
        JsonNode previous = page.path("items").get(1);
        assertThat(current.path("current").asBoolean()).isTrue();
        assertThat(current.path("status").asText()).isEqualTo("OPEN");
        assertThat(current.path("actions").path("Guardó una medición").asLong()).isEqualTo(1);
        assertThat(previous.path("status").asText()).isEqualTo("ENDED");
        assertThat(previous.path("statusLabel").asText()).isEqualTo("Cerró sesión");
        assertThat(previous.path("client").asText()).isEqualTo("Chrome en Windows");
        assertThat(previous.path("actions").path("Guardó una medición").asLong()).isEqualTo(2);
        assertThat(previous.path("actionCount").asLong()).isEqualTo(2);

        JsonNode detail = json(mvc.perform(get("/api/v1/users/me/sessions/" + previous.path("id").asLong())
                .header("Authorization", "Bearer " + second)).andExpect(status().isOk()).andReturn());
        assertThat(detail.path("actions").findValuesAsText("action"))
            .containsExactly("USER_REGISTERED", "MEASUREMENT_SAVED", "MEASUREMENT_SAVED", "USER_LOGOUT");

        // Another user cannot read this history.
        String other = token(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Otra\",\"email\":\"otra-" + UUID.randomUUID() + "@aquasense.test\",\"password\":\"" + PASSWORD + "\"}"))
            .andReturn());
        mvc.perform(get("/api/v1/users/me/sessions/" + previous.path("id").asLong()).header("Authorization", "Bearer " + other))
            .andExpect(status().isNotFound());
    }

    @Test
    void otherOpenSessionsCanBeClosedFromTheCurrentOne() throws Exception {
        String email = "otras-" + UUID.randomUUID() + "@aquasense.test";
        String laptop = token(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Usuario Equipos\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andReturn());
        String phone = login(email);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + laptop)).andExpect(status().isOk());

        JsonNode closed = json(mvc.perform(post("/api/v1/users/me/sessions/close-others").header("Authorization", "Bearer " + phone))
            .andExpect(status().isOk()).andReturn());
        assertThat(closed.path("message").asText()).contains("1 sesión");
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + laptop)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + phone)).andExpect(status().isOk());
        JsonNode page = json(mvc.perform(get("/api/v1/users/me/sessions").header("Authorization", "Bearer " + phone)).andReturn());
        assertThat(page.path("items").get(1).path("statusLabel").asText()).isEqualTo("Cerrada desde otra sesión");
    }

    private void measure(String token, double ph) throws Exception {
        mvc.perform(post("/api/v1/measurements").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"ph\":" + ph + "}")).andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        return token(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk()).andReturn());
    }

    private String token(MvcResult result) throws Exception {
        return json(result).path("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
