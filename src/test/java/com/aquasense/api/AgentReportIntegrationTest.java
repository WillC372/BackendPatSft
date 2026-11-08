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
 * El agente completo contra un servidor falso que responde llamadas a herramientas en formato OpenAI:
 * consulta estadísticas y anomalías, entrega el informe y el sistema lo verifica, guarda y exporta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentReportIntegrationTest {

    private static final FakeAiServer SERVER;

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
        registry.add(p + "model", () -> "openai/gpt-oss-120b");
        registry.add(p + "timeout-seconds", () -> 5);
        registry.add(p + "tools", () -> true);
        registry.add("aquasense.ai.daily-quota-free", () -> 20);
    }

    @AfterAll
    static void stop() {
        SERVER.close();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String toolCalls(String calls) {
        return "{\"model\":\"openai/gpt-oss-120b\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"tool_calls\":[" + calls
            + "]},\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":500,\"completion_tokens\":60}}";
    }

    private static String call(String id, String name, String argsJson) {
        return "{\"id\":\"" + id + "\",\"type\":\"function\",\"function\":{\"name\":\"" + name + "\",\"arguments\":\""
            + argsJson.replace("\"", "\\\"") + "\"}}";
    }

    @Test
    void agentInvestigatesSubmitsAndPersistsTheReport() throws Exception {
        String token = register();
        for (double ph : new double[] {7.3, 7.1, 6.9, 7.0, 7.2}) {
            mvc.perform(post("/api/v1/measurements").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ph\":" + ph + "}")).andExpect(status().isCreated());
        }
        String report = "{\"title\":\"Informe de prueba\",\"executiveSummary\":\"Promedio 7.1 con mínimo 6.9.\","
            + "\"keyMetrics\":[{\"label\":\"Promedio\",\"value\":\"7.1\"}],"
            + "\"sections\":[{\"heading\":\"Estado\",\"body\":\"Valores entre 6.9 y 7.3.\",\"chart\":\"PH_EVOLUTION\"},"
            + "{\"heading\":\"Anomalías\",\"body\":\"No se detectaron.\",\"chart\":\"ANOMALIES\"}],"
            + "\"recommendations\":[{\"action\":\"Seguir midiendo\",\"priority\":2,\"rationale\":\"datos estables\"}],\"limitations\":[\"Solo 5 mediciones\"],"
            + "\"citedSourceIds\":[\"epa-ph-smcl\",\"norma-inventada\"]}";
        SERVER.reply(200, toolCalls(call("a", "get_water_profile", "{}") + "," + call("b", "get_statistics", "{\"days\":7}")));
        SERVER.reply(200, toolCalls(call("c", "get_anomalies", "{\"days\":7}") + ","
            + call("e", "search_guidelines", "{\"query\":\"rango recomendado agua de consumo\"}")));
        SERVER.reply(200, toolCalls(call("d", "submit_report", report)));

        JsonNode job = json(mvc.perform(post("/api/v1/intelligence/agent-reports").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"days\":7,\"focus\":\"¿Está estable mi agua?\"}"))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        JsonNode done = poll(token, job.path("jobId").asText());
        assertThat(done.path("status").asText()).isEqualTo("DONE");
        JsonNode result = done.path("report");
        assertThat(result.path("source").asText()).isEqualTo("AI");
        assertThat(result.path("toolCalls").asInt()).isEqualTo(4);
        assertThat(result.path("trace").size()).isEqualTo(5);
        assertThat(result.path("citations").size()).isEqualTo(1);
        assertThat(result.path("citations").get(0).path("id").asText()).isEqualTo("epa-ph-smcl");
        assertThat(result.path("citations").get(0).path("quote").asText()).startsWith("pH 6.5 - 8.5");
        assertThat(result.path("discardedCitations").get(0).asText()).isEqualTo("norma-inventada");
        assertThat(result.path("report").path("citedSourceIds").size()).isEqualTo(1);
        assertThat(result.path("verification").path("checked").asInt()).isEqualTo(5);
        assertThat(result.path("verification").path("verified").asInt()).isEqualTo(5);
        assertThat(SERVER.requests().get(0)).contains("\"tool_choice\":\"required\"").contains("submit_report");
        assertThat(SERVER.requests().get(1)).contains("\"role\":\"tool\"").contains("rangoIdeal");

        long id = result.path("id").asLong();
        mvc.perform(get("/api/v1/intelligence/agent-reports/" + id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        String text = mvc.perform(get("/api/v1/intelligence/agent-reports/" + id + "/text").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).contains("Informe de prueba").contains("CÓMO SE ELABORÓ").contains("FUENTES NORMATIVAS")
            .doesNotContain("norma-inventada");
        var pdfResponse = mvc.perform(get("/api/v1/intelligence/agent-reports/" + id + "/pdf").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(pdfResponse.getContentType()).isEqualTo("application/pdf");
        assertThat(pdfResponse.getHeader("Content-Disposition")).contains(".pdf");
        String pdfText = pdfText(pdfResponse.getContentAsByteArray());
        assertThat(pdfText).contains("Informe de prueba").contains("Promedio 7.1 con mínimo 6.9.")
            .contains("Cómo se elaboró").contains("Fuentes normativas citadas").contains("pH 6.5 - 8.5")
            .contains("Página 1 de").doesNotContain("norma-inventada");
        String rulesText = pdfText(mvc.perform(get("/api/v1/reports/pdf").param("days", "7").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(rulesText).contains("Informe de calidad del agua").contains("últimos 7 días").contains("Conclusión");
        String other = register();
        mvc.perform(get("/api/v1/intelligence/agent-reports/" + id).header("Authorization", "Bearer " + other))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/intelligence/agent-reports/" + id + "/pdf").header("Authorization", "Bearer " + other))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/intelligence/agent-reports/jobs/" + job.path("jobId").asText()).header("Authorization", "Bearer " + other))
            .andExpect(status().isNotFound());

        SERVER.reply(503, "{\"error\":{}}");
        JsonNode fallbackJob = json(mvc.perform(post("/api/v1/intelligence/agent-reports").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"days\":7}")).andReturn().getResponse().getContentAsString());
        JsonNode fallback = poll(token, fallbackJob.path("jobId").asText()).path("report");
        assertThat(fallback.path("source").asText()).isEqualTo("RULES");
        assertThat(fallback.path("note").asText()).contains("reglas");
        assertThat(fallback.path("citations").size()).isGreaterThan(0);
        JsonNode history = json(mvc.perform(get("/api/v1/intelligence/agent-reports").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString());
        assertThat(history.size()).isEqualTo(2);
    }

    /** Extrae el texto del PDF: comprueba que el contenido y las tildes quedaron incrustados, no solo que hay bytes. */
    private static String pdfText(byte[] bytes) throws IOException {
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isPositive();
            return new org.apache.pdfbox.text.PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    private JsonNode poll(String token, String jobId) throws Exception {
        for (int i = 0; i < 100; i++) {
            JsonNode state = json(mvc.perform(get("/api/v1/intelligence/agent-reports/jobs/" + jobId)
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
            if (!"RUNNING".equals(state.path("status").asText())) {
                return state;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Job did not finish");
    }

    private String register() throws Exception {
        String email = "a" + UUID.randomUUID().toString().substring(0, 8) + "@aquasense.test";
        return json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Prueba\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andReturn().getResponse().getContentAsString()).path("token").asText();
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }
}
