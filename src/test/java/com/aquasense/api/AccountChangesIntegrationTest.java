package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Changing the password and the name from inside the app. */
@SpringBootTest
@AutoConfigureMockMvc
class AccountChangesIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void changingThePasswordKeepsThisSessionAndClosesTheOthers() throws Exception {
        String email = "clave-" + UUID.randomUUID() + "@aquasense.test";
        String laptop = json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Usuario Clave\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn()).path("token").asText();
        String phone = login(email, "Clave1234");

        mvc.perform(post("/api/v1/users/me/password").header("Authorization", "Bearer " + laptop).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"Equivocada1\",\"newPassword\":\"Nueva12345\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users/me/password").header("Authorization", "Bearer " + laptop).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"Clave1234\",\"newPassword\":\"corta\"}")).andExpect(status().isBadRequest());

        String renewed = json(mvc.perform(post("/api/v1/users/me/password").header("Authorization", "Bearer " + laptop)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"Clave1234\",\"newPassword\":\"Nueva12345\"}"))
            .andExpect(status().isOk()).andReturn()).path("token").asText();

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + renewed)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + laptop)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + phone)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"Clave1234\"}")).andExpect(status().isUnauthorized());
        login(email, "Nueva12345");

        JsonNode sessions = json(mvc.perform(get("/api/v1/users/me/sessions").header("Authorization", "Bearer " + renewed)).andReturn());
        assertThat(sessions.toString()).contains("Cerrada al cambiar la contraseña").contains("Cambió su contraseña");
    }

    @Test
    void theNameCanBeChangedExceptOnDemoAccounts() throws Exception {
        String token = json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Nombre Viejo\",\"email\":\"nombre-" + UUID.randomUUID() + "@aquasense.test\",\"password\":\"Clave1234\"}"))
            .andReturn()).path("token").asText();
        JsonNode user = json(mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"  Ana   María  Pérez \"}")).andExpect(status().isOk()).andReturn());
        assertThat(user.path("fullName").asText()).isEqualTo("Ana María Pérez");
        mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"   \"}")).andExpect(status().isBadRequest());

        JsonNode demos = json(mvc.perform(get("/api/v1/auth/demo-accounts")).andReturn());
        String demo = login(demos.get(0).path("email").asText(), demos.get(0).path("password").asText());
        mvc.perform(put("/api/v1/users/me").header("Authorization", "Bearer " + demo).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"Otro\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/me/password").header("Authorization", "Bearer " + demo).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"" + demos.get(0).path("password").asText() + "\",\"newPassword\":\"Nueva12345\"}"))
            .andExpect(status().isForbidden());
    }

    private String login(String email, String password) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andExpect(status().isOk()).andReturn())
            .path("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
