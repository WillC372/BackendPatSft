package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** One demonstration account per role, usable from the login buttons and limited so it stays usable for everyone. */
@SpringBootTest
@AutoConfigureMockMvc
class DemoAccountsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void eachRoleHasADemoAccountThatLogsInWithTheShownCredentials() throws Exception {
        JsonNode accounts = json(mvc.perform(get("/api/v1/auth/demo-accounts")).andExpect(status().isOk()).andReturn());
        assertThat(accounts.findValuesAsText("key")).containsExactly("USER", "PREMIUM", "ADMIN");
        JsonNode user = login(accounts.get(0)).path("user");
        JsonNode premium = login(accounts.get(1)).path("user");
        JsonNode admin = login(accounts.get(2)).path("user");
        assertThat(user.path("plan").asText()).isEqualTo("FREE");
        assertThat(user.path("demo").asBoolean()).isTrue();
        assertThat(premium.path("plan").asText()).isEqualTo("PREMIUM");
        assertThat(admin.path("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void demoAccountsCannotBeDeletedAndTheDemoAdministratorOnlyLooks() throws Exception {
        JsonNode accounts = json(mvc.perform(get("/api/v1/auth/demo-accounts")).andReturn());
        String user = login(accounts.get(0)).path("token").asText();
        mvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + user).contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"" + accounts.get(0).path("password").asText() + "\"}")).andExpect(status().isForbidden());

        String realEmail = "real-" + UUID.randomUUID() + "@aquasense.test";
        long realId = json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Persona Real Privada\",\"email\":\"" + realEmail + "\",\"password\":\"Clave1234\"}"))
            .andReturn()).path("user").path("id").asLong();

        JsonNode adminLogin = login(accounts.get(2));
        String admin = adminLogin.path("token").asText();
        JsonNode page = json(mvc.perform(get("/api/v1/admin/users").param("size", "100").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andReturn());
        assertThat(page.toString()).doesNotContain(realEmail).doesNotContain("Persona Real Privada");
        assertThat(page.toString()).contains("r***@aquasense.test").contains("usuario@demo.aquasense");

        mvc.perform(get("/api/v1/admin/users/" + realId).header("Authorization", "Bearer " + admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users/" + realId + "/sessions").header("Authorization", "Bearer " + admin))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/users/" + realId + "/plan").header("Authorization", "Bearer " + admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PREMIUM\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/users/" + realId).header("Authorization", "Bearer " + admin)).andExpect(status().isForbidden());

        long demoUserId = login(accounts.get(0)).path("user").path("id").asLong();
        JsonNode detail = json(mvc.perform(get("/api/v1/admin/users/" + demoUserId).header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andReturn());
        assertThat(detail.path("guards").path("deleteBlockedReason").asText()).contains("demostración");
    }

    private JsonNode login(JsonNode account) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + account.path("email").asText() + "\",\"password\":\"" + account.path("password").asText() + "\"}"))
            .andExpect(status().isOk()).andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
