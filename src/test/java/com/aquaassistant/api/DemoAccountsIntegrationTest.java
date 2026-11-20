package com.aquaassistant.api;

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

/**
 * One demonstration account per role, usable from the login buttons. They can do everything a normal account of their
 * role does, and every login with the button puts them back as offered so the buttons always work.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoAccountsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void threeButtonsUserPremiumAndAdministrator() throws Exception {
        JsonNode accounts = accounts();
        assertThat(accounts.findValuesAsText("key")).containsExactly("USER", "PREMIUM", "ADMIN");
        assertThat(accounts.findValuesAsText("label")).containsExactly("Usuario", "Premium", "Administrador");
        JsonNode user = login(accounts.get(0)).path("user");
        JsonNode premium = login(accounts.get(1)).path("user");
        JsonNode admin = login(accounts.get(2)).path("user");
        assertThat(user.path("demo").asBoolean()).isTrue();
        assertThat(user.path("plan").asText()).isEqualTo("FREE");
        assertThat(premium.path("plan").asText()).isEqualTo("PREMIUM");
        assertThat(admin.path("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void aDemoAccountCanChangeItsPasswordAndBeDeletedAndTheButtonStillWorks() throws Exception {
        JsonNode account = accounts().get(0);
        String password = account.path("password").asText();
        JsonNode first = login(account);
        String token = first.path("token").asText();
        mvc.perform(post("/api/v1/users/me/password").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"" + password + "\",\"newPassword\":\"OtraClave2026\"}")).andExpect(status().isOk());
        // The button keeps working with the shown password.
        String again = login(account).path("token").asText();
        mvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + again).contentType(MediaType.APPLICATION_JSON)
            .content("{\"password\":\"" + password + "\"}")).andExpect(status().is2xxSuccessful());
        JsonNode recreated = login(account).path("user");
        assertThat(recreated.path("id").asLong()).isNotEqualTo(first.path("user").path("id").asLong());
        assertThat(recreated.path("plan").asText()).isEqualTo("FREE");
    }

    @Test
    void theDemoAdministratorManagesDemoAndExampleAccountsButRealOnesStayPrivate() throws Exception {
        JsonNode accounts = accounts();
        String realEmail = "real-" + UUID.randomUUID() + "@aquasense.test";
        long realId = register("Persona Real Privada", realEmail);
        String exampleEmail = "ejemplo-" + UUID.randomUUID() + "@example.com";
        long exampleId = register("Cuenta De Ejemplo", exampleEmail);

        String admin = login(accounts.get(2)).path("token").asText();
        JsonNode page = json(mvc.perform(get("/api/v1/admin/users").param("size", "100").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andReturn());
        assertThat(page.toString()).doesNotContain(realEmail).doesNotContain("Persona Real Privada")
            .contains("r***@aquasense.test").contains(exampleEmail).contains("usuario@demo.aquaassistant");

        mvc.perform(get("/api/v1/admin/users/" + realId).header("Authorization", "Bearer " + admin)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/users/" + realId + "/plan").header("Authorization", "Bearer " + admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PREMIUM\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/users/" + realId).header("Authorization", "Bearer " + admin)).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/users/" + exampleId).header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/users/" + exampleId + "/plan").header("Authorization", "Bearer " + admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PREMIUM\"}")).andExpect(status().isOk());

        // A demonstration account can be changed too; its next login puts it back on the free plan.
        long demoUserId = login(accounts.get(0)).path("user").path("id").asLong();
        mvc.perform(put("/api/v1/admin/users/" + demoUserId + "/plan").header("Authorization", "Bearer " + admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PREMIUM\"}")).andExpect(status().isOk());
        assertThat(login(accounts.get(0)).path("user").path("plan").asText()).isEqualTo("FREE");
    }

    private JsonNode accounts() throws Exception {
        return json(mvc.perform(get("/api/v1/auth/demo-accounts")).andExpect(status().isOk()).andReturn());
    }

    private long register(String name, String email) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andReturn()).path("user").path("id").asLong();
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
