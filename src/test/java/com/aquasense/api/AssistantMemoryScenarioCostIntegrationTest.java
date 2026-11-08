package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Assistant memory, AI scenario explanation and cost tracking against a local fake provider.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistantMemoryScenarioCostIntegrationTest {

    private static final FakeAiServer SERVER;
    private static final String MODEL = "openai/gpt-oss-120b";

    static {
        try {
            SERVER = new FakeAiServer();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        String p = "aquasense.ai.providers[0].";
        registry.add(p + "id", () -> "groq");
        registry.add(p + "label", () -> "Groq");
        registry.add(p + "base-url", SERVER::baseUrl);
        registry.add(p + "api-key", () -> "fake");
        registry.add(p + "model", () -> MODEL);
        registry.add(p + "timeout-seconds", () -> 5);
        registry.add("aquasense.ai.daily-quota-free", () -> 20);
        registry.add("aquasense.ai-pricing.billing", () -> "FREE");
        registry.add("aquasense.ai-pricing.models[0].model", () -> MODEL);
        registry.add("aquasense.ai-pricing.models[0].input-per-million", () -> 0.15);
        registry.add("aquasense.ai-pricing.models[0].output-per-million", () -> 0.60);
        registry.add("aquasense.ai-pricing.models[0].source", () -> "prueba");
        registry.add("aquasense.ai-pricing.models[0].verified", () -> false);
    }

    @AfterAll
    static void stop() {
        SERVER.close();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void assistantRemembersScenarioIsExplainedAndCostsAreTracked() throws Exception {
        String token = register();
        for (double ph : new double[] {7.4, 7.3, 7.0}) {
            mvc.perform(post("/api/v1/measurements").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ph\":" + ph + "}")).andExpect(status().isCreated());
        }

        // Memory: two turns in the same conversation.
        SERVER.replyContent(MODEL, "Tu pH bajó a 7.0 y sigue en rango [epa-ph-smcl]. Otra norma [norma-inventada] no aplica.");
        JsonNode first = json(chat(token, "¿Cómo está mi agua?", null).andExpect(status().isOk()));
        long conversation = first.path("conversationId").asLong();
        assertThat(conversation).isPositive();
        assertThat(first.path("citations").size()).isEqualTo(1);
        assertThat(first.path("removedReferences").asInt()).isEqualTo(1);
        assertThat(first.path("answer").asText()).doesNotContain("norma-inventada");
        assertThat(first.path("memoryTurns").asInt()).isZero();

        SERVER.replyContent(MODEL, "Sí: como te dije, 7.0 está dentro del rango [epa-ph-smcl].");
        JsonNode second = json(chat(token, "¿Y eso es grave?", conversation).andExpect(status().isOk()));
        assertThat(second.path("conversationId").asLong()).isEqualTo(conversation);
        assertThat(second.path("memoryTurns").asInt()).isEqualTo(2);
        assertThat(second.path("citations").size()).isEqualTo(1);
        String secondRequest = SERVER.requests().get(1);
        assertThat(secondRequest).contains("¿Cómo está mi agua?").contains("\"role\":\"assistant\"").contains("Tu pH bajó a 7.0");

        JsonNode list = json(mvc.perform(get("/api/v1/intelligence/chat/conversations").header("Authorization", "Bearer " + token)));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("messageCount").asInt()).isEqualTo(4);
        assertThat(list.get(0).path("title").asText()).isEqualTo("¿Cómo está mi agua?");
        JsonNode detail = json(mvc.perform(get("/api/v1/intelligence/chat/conversations/" + conversation)
            .header("Authorization", "Bearer " + token)));
        assertThat(detail.path("messages").size()).isEqualTo(4);
        assertThat(detail.path("messages").get(1).path("citations").get(0).path("id").asText()).isEqualTo("epa-ph-smcl");
        String other = register();
        mvc.perform(get("/api/v1/intelligence/chat/conversations/" + conversation).header("Authorization", "Bearer " + other))
            .andExpect(status().isNotFound());
        chat(other, "Hola", conversation).andExpect(status().isNotFound());

        // Scenario: the AI cites a nonexistent guideline, invents a figure and marks nothing as critical.
        SERVER.replyContent(MODEL, """
            {"headline":"Con pH 5.5 el agua sería ácida y corrosiva.",
             "explanation":"Según la EPA el rango es 6.5 - 8.5; con 5.5 el agua corroería tuberías. Podría bajar hasta 4.2.",
             "effects":[{"area":"Tuberías","effect":"Corrosión de cobre y hierro","severity":"CAUTION"}],
             "earlySignals":["Sabor metálico"],"preventiveActions":["Medir a diario"],
             "citedSourceIds":["epa-corrosion","norma-falsa"]}""");
        JsonNode scenario = json(mvc.perform(post("/api/v1/intelligence/scenario/explain").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"hypotheticalPh\":5.5}")).andExpect(status().isOk()));
        assertThat(scenario.path("source").asText()).isEqualTo("AI");
        assertThat(scenario.path("hydrogenFactor").asDouble()).isEqualTo(31.6);
        assertThat(scenario.path("effects").get(0).path("severity").asText()).isEqualTo("CRITICAL");
        assertThat(scenario.path("citations").get(0).path("id").asText()).isEqualTo("epa-corrosion");
        assertThat(scenario.path("adjustments").toString()).contains("norma-falsa").contains("crítica");
        assertThat(scenario.path("verification").path("unverified").toString()).contains("4.2");
        assertThat(scenario.path("verification").path("verified").asInt()).isEqualTo(4);
        assertThat(SERVER.requests().get(2)).contains("factorConcentracionHidrogeno").contains("scenario_explanation");

        // A total provider failure is recorded even if the operation is rolled back.
        SERVER.reply(503, "{\"error\":{}}");
        chat(token, "¿Otra pregunta?", conversation).andExpect(status().isServiceUnavailable());

        JsonNode usage = json(mvc.perform(get("/api/v1/intelligence/ai-usage").header("Authorization", "Bearer " + token)));
        assertThat(usage.path("totals").path("calls").asInt()).isEqualTo(3);
        assertThat(usage.path("totals").path("failures").asInt()).isEqualTo(1);
        assertThat(usage.path("totals").path("estimatedUsd").decimalValue()).isEqualByComparingTo("0.000198");
        assertThat(usage.path("totals").path("chargedUsd").decimalValue()).isEqualByComparingTo("0");
        assertThat(usage.path("byModel").get(0).path("model").asText()).isEqualTo(MODEL);
        assertThat(usage.path("byModel").get(0).path("lastError").asText()).startsWith("Groq:");
        assertThat(usage.path("byPurpose").toString()).contains("Asistente").contains("Simulador de escenarios");
        assertThat(usage.path("daily").size()).isEqualTo(30);
        assertThat(usage.path("usedToday").asInt()).isEqualTo(3);
        mvc.perform(get("/api/v1/admin/ai-usage").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/intelligence/chat/conversations/" + conversation).header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        assertThat(json(mvc.perform(get("/api/v1/intelligence/chat/conversations").header("Authorization", "Bearer " + token)))
            .size()).isZero();
    }

    private ResultActions chat(String token, String question, Long conversationId) throws Exception {
        String body = "{\"question\":\"" + question + "\"" + (conversationId == null ? "" : ",\"conversationId\":" + conversationId) + "}";
        return mvc.perform(post("/api/v1/intelligence/chat").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String register() throws Exception {
        String email = "m" + UUID.randomUUID().toString().substring(0, 8) + "@aquasense.test";
        return mapper.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Prueba\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andReturn().getResponse().getContentAsString()).path("token").asText();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
}
