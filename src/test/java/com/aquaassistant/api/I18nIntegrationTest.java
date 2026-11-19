package com.aquaassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
class I18nIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void theLanguageAndTheVoiceAreSavedInTheSettings() throws Exception {
        String token = register();
        mvc.perform(put("/api/v1/users/me/settings").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"LIGHT\",\"notificationsEnabled\":true,\"autoSave\":false,\"language\":\"zh-hans\",\"voiceEnabled\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.language").value("zh-Hans")).andExpect(jsonPath("$.voiceEnabled").value(false));
        mvc.perform(get("/api/v1/users/me/settings").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.voiceEnabled").value(false)).andExpect(jsonPath("$.language").value("zh-Hans"));
    }

    private String register() throws Exception {
        String email = "i" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Persona Idioma\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn();
        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
