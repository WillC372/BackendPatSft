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
    void theLanguagesAreOpenBeforeSigningIn() throws Exception {
        JsonNode list = json(mvc.perform(get("/api/v1/i18n/languages")).andExpect(status().isOk()).andReturn());
        assertThat(list.size()).isGreaterThan(50);
        assertThat(list.get(0).get("code").asText()).isEqualTo("es");
        assertThat(list.findValuesAsText("code")).contains("en", "pt-BR", "zh-Hans", "ar", "qu");
        mvc.perform(get("/api/v1/i18n/dictionary/fr")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/i18n/dictionary/xx")).andExpect(status().isBadRequest());
    }

    @Test
    void withoutAnAiTheTextsComeBackEmptyAndSpanishIsReturnedAsIs() throws Exception {
        mvc.perform(post("/api/v1/i18n/translate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"lang\":\"fr\",\"texts\":[\"Medir\",\"7.2\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lang").value("fr"))
            .andExpect(jsonPath("$.translations[0]").doesNotExist()).andExpect(jsonPath("$.translations[1]").value("7.2"));
        mvc.perform(post("/api/v1/i18n/translate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"lang\":\"es\",\"texts\":[\"Medir\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.translations[0]").value("Medir"));
    }

    @Test
    void theLanguageAndTheVoiceAreSavedInTheSettings() throws Exception {
        String token = register();
        mvc.perform(put("/api/v1/users/me/settings").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"LIGHT\",\"notificationsEnabled\":true,\"autoSave\":false,\"language\":\"pt-br\",\"voiceEnabled\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.language").value("pt-BR")).andExpect(jsonPath("$.voiceEnabled").value(false));
        mvc.perform(get("/api/v1/users/me/settings").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.voiceEnabled").value(false)).andExpect(jsonPath("$.language").value("pt-BR"));
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
