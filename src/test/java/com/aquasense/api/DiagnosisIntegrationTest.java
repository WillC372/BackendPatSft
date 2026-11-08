package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquasense.support.FakeAiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full diagnosis flow with two local fake providers: the first one is overloaded (503)
 * and the second answers valid JSON. Checks the fallback chain, the validation and the persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagnosisIntegrationTest {

    private static final FakeAiServer OVERLOADED;
    private static final FakeAiServer BACKUP;

    static {
        try {
            OVERLOADED = new FakeAiServer();
            BACKUP = new FakeAiServer();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        registry.add("aquasense.ai.providers[0].id", () -> "gemini");
        registry.add("aquasense.ai.providers[0].label", () -> "Google Gemini");
        registry.add("aquasense.ai.providers[0].base-url", OVERLOADED::baseUrl);
        registry.add("aquasense.ai.providers[0].api-key", () -> "fake");
        registry.add("aquasense.ai.providers[0].model", () -> "gemini-3.5-flash");
        registry.add("aquasense.ai.providers[0].timeout-seconds", () -> 5);
        registry.add("aquasense.ai.providers[1].id", () -> "groq");
        registry.add("aquasense.ai.providers[1].label", () -> "Groq");
        registry.add("aquasense.ai.providers[1].base-url", BACKUP::baseUrl);
        registry.add("aquasense.ai.providers[1].api-key", () -> "fake");
        registry.add("aquasense.ai.providers[1].model", () -> "openai/gpt-oss-120b");
        registry.add("aquasense.ai.providers[1].timeout-seconds", () -> 5);
        registry.add("aquasense.ai.daily-quota-free", () -> 10);
    }

    @AfterAll
    static void stop() {
        OVERLOADED.close();
        BACKUP.close();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void diagnosisUsesBackupProviderValidatesAndPersists() throws Exception {
        String token = register();
        JsonNode empty = json(mvc.perform(post("/api/v1/intelligence/diagnosis").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(empty.path("source").asText()).isEqualTo("RULES");
        assertThat(empty.path("note").asText()).contains("Sin mediciones");

        for (double ph : new double[] {7.4, 7.3, 7.0}) {
            mvc.perform(post("/api/v1/measurements").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ph\":" + ph + "}")).andExpect(status().isCreated());
        }
        OVERLOADED.reply(503, "{\"error\":{\"message\":\"high demand\"}}");
        BACKUP.replyContent("openai/gpt-oss-120b", """
            {"riskLevel":"LOW","summary":"El pH bajó a 7.0 y salió del rango ideal de la piscina.",
             "findings":[{"title":"Descenso de pH","evidence":"7.4 -> 7.0 en el periodo","severity":"CAUTION"}],
             "probableCauses":[{"cause":"Lluvia","likelihood":"MEDIUM","evidence":"Descenso reciente"}],
             "actions":[{"action":"Volver a medir","priority":1,"timeframe":"Hoy"}],
             "dataGaps":["Falta alcalinidad total"],"confidence":0.9,"nextMeasurementHours":72}""");

        JsonNode result = json(mvc.perform(post("/api/v1/intelligence/diagnosis").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("source").asText()).isEqualTo("AI");
        assertThat(result.path("provider").asText()).isEqualTo("Groq");
        assertThat(result.path("confidence").asDouble()).isEqualTo(0.4);
        assertThat(result.path("adjustments").size()).isGreaterThanOrEqualTo(1);
        assertThat(BACKUP.requests().get(0)).contains("lastMeasurements").contains("json_schema").doesNotContain("@aquasense.test");

        JsonNode latest = json(mvc.perform(get("/api/v1/intelligence/diagnosis/latest").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(latest.path("id").asLong()).isEqualTo(result.path("id").asLong());
        JsonNode history = json(mvc.perform(get("/api/v1/intelligence/diagnosis/history").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString());
        assertThat(history.size()).isEqualTo(2);

        OVERLOADED.reply(503, "{}");
        BACKUP.replyContent("openai/gpt-oss-120b", "{\"riskLevel\":\"INVENTADO\"}");
        JsonNode fallback = json(mvc.perform(post("/api/v1/intelligence/diagnosis?days=30").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString());
        assertThat(fallback.path("source").asText()).isEqualTo("RULES");
        assertThat(fallback.path("note").asText()).contains("validación");

        JsonNode status = json(mvc.perform(get("/api/v1/intelligence/ai-status").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString());
        assertThat(status.path("providers").get(0).path("provider").asText()).isEqualTo("Google Gemini");
        assertThat(status.path("providers").get(1).path("model").asText()).isEqualTo("openai/gpt-oss-120b");
    }

    private String register() throws Exception {
        String email = "d" + UUID.randomUUID().toString().substring(0, 8) + "@aquasense.test";
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Prueba\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andReturn().getResponse().getContentAsString();
        return json(body).path("token").asText();
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }
}
