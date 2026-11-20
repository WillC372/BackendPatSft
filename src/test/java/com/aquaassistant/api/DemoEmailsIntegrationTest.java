package com.aquaassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** H-058: the login buttons can use real mailboxes, given in the configuration. */
@SpringBootTest(properties = {"aquasense.demo.user-email=Aqua.Usuario.Demo@gmail.com",
    "aquasense.demo.premium-email=aqua.premium.demo@gmail.com", "aquasense.demo.admin-email=aqua.admin.demo@gmail.com"})
@AutoConfigureMockMvc
class DemoEmailsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void theButtonsUseTheConfiguredAddresses() throws Exception {
        JsonNode accounts = mapper.readTree(mvc.perform(get("/api/v1/auth/demo-accounts")).andReturn().getResponse().getContentAsString());
        assertThat(accounts.findValuesAsText("email"))
            .containsExactly("aqua.usuario.demo@gmail.com", "aqua.premium.demo@gmail.com", "aqua.admin.demo@gmail.com");
        JsonNode user = mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"aqua.usuario.demo@gmail.com\",\"password\":\"" + accounts.get(0).path("password").asText() + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("user");
        assertThat(user.path("demo").asBoolean()).isTrue();
        assertThat(user.path("plan").asText()).isEqualTo("FREE");
    }
}
