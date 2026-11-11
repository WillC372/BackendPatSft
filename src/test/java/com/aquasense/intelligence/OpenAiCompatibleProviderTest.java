package com.aquasense.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.config.AppProperties;
import com.aquasense.intelligence.ai.AiAnswer;
import com.aquasense.intelligence.ai.AiPrompt;
import com.aquasense.intelligence.ai.AiUnavailableException;
import com.aquasense.intelligence.ai.OpenAiCompatibleProvider;
import com.aquasense.intelligence.diagnosis.DiagnosisSchema;
import com.aquasense.support.FakeAiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private FakeAiServer server;
    private OpenAiCompatibleProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeAiServer();
        provider = new OpenAiCompatibleProvider(
            new AppProperties.Provider("groq", "Groq", server.baseUrl(), "test-key", "openai/gpt-oss-120b", 5, true, true, true, "low"), mapper);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void sendsBearerKeyUserAgentAndStrictJsonSchema() throws Exception {
        server.replyContent("openai/gpt-oss-120b", "{\"ok\":true}");
        AiAnswer answer = provider.complete(new AiPrompt(1L, "DIAGNOSIS", "sistema", "hechos",
            DiagnosisSchema.NAME, DiagnosisSchema.SCHEMA));
        assertThat(answer.text()).isEqualTo("{\"ok\":true}");
        assertThat(answer.provider()).isEqualTo("Groq");
        assertThat(answer.inputTokens()).isEqualTo(120);
        assertThat(server.authorizations().get(0)).isEqualTo("Bearer test-key");
        assertThat(server.userAgents().get(0)).startsWith("AquaSense-AI");
        JsonNode body = mapper.readTree(server.requests().get(0));
        assertThat(body.path("model").asText()).isEqualTo("openai/gpt-oss-120b");
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(body.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(body.at("/response_format/json_schema/schema/properties/riskLevel/enum").size()).isEqualTo(4);
    }

    @Test
    void streamsTextFragmentsAsTheModelWritesThem() throws Exception {
        server.reply(200, String.join("\n",
            "data: {\"model\":\"openai/gpt-oss-120b\",\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}", "",
            "data: {\"choices\":[{\"delta\":{\"content\":\"El pH \"}}]}", "",
            "data: {\"choices\":[{\"delta\":{\"content\":\"es neutro.\"}}]}", "",
            "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":50,\"completion_tokens\":4}}", "",
            "data: [DONE]", ""));
        java.util.List<String> deltas = new java.util.ArrayList<>();
        AiAnswer answer = provider.stream(new AiPrompt(1L, "CHAT", "sistema", "pregunta"), deltas::add);
        assertThat(deltas).containsExactly("El pH ", "es neutro.");
        assertThat(answer.text()).isEqualTo("El pH es neutro.");
        assertThat(answer.inputTokens()).isEqualTo(50);
        assertThat(answer.outputTokens()).isEqualTo(4);
        JsonNode body = mapper.readTree(server.requests().get(0));
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("low");
    }

    @Test
    void aStreamThatDoesNotStartInTimeIsAbandonedSoTheNextModelCanAnswer() throws Exception {
        java.lang.reflect.Method shorter = OpenAiCompatibleProvider.class.getDeclaredMethod("withStreamFirstResponse", java.time.Duration.class);
        shorter.setAccessible(true);
        shorter.invoke(provider, java.time.Duration.ofMillis(300));
        server.delay(1500).reply(200, "data: {\"choices\":[{\"delta\":{\"content\":\"tarde\"}}]}\n\ndata: [DONE]\n\n");
        long start = System.nanoTime();
        assertThatThrownBy(() -> provider.stream(new AiPrompt(1L, "CHAT", "sistema", "pregunta"), d -> { }))
            .isInstanceOf(AiUnavailableException.class).hasMessageContaining("no empezó a responder");
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1400);
    }

    private void shortWaits(java.time.Duration first, java.time.Duration stall) throws Exception {
        for (String name : new String[] {"withStreamFirstResponse", "withStreamStall"}) {
            java.lang.reflect.Method m = OpenAiCompatibleProvider.class.getDeclaredMethod(name, java.time.Duration.class);
            m.setAccessible(true);
            m.invoke(provider, name.endsWith("Stall") ? stall : first);
        }
    }

    @Test
    void aModelThatSendsHeadersButNoTextIsAbandoned() throws Exception {
        shortWaits(java.time.Duration.ofMillis(400), java.time.Duration.ofSeconds(5));
        server.stallAfter(": keep-alive\n\n", 3000).reply(200, "data: {\"choices\":[{\"delta\":{\"content\":\"tarde\"}}]}\n\n");
        long start = System.nanoTime();
        assertThatThrownBy(() -> provider.stream(new AiPrompt(1L, "CHAT", "sistema", "pregunta"), d -> { }))
            .isInstanceOf(AiUnavailableException.class).hasMessageContaining("no empezó a escribir");
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2000);
    }

    @Test
    void anAnswerThatStopsInTheMiddleIsCutAndReported() throws Exception {
        shortWaits(java.time.Duration.ofSeconds(5), java.time.Duration.ofMillis(400));
        server.stallAfter("data: {\"choices\":[{\"delta\":{\"content\":\"El pH \"}}]}\n\n", 3000)
            .reply(200, "data: {\"choices\":[{\"delta\":{\"content\":\"es 7.\"}}]}\n\n");
        java.util.List<String> deltas = new java.util.ArrayList<>();
        long start = System.nanoTime();
        assertThatThrownBy(() -> provider.stream(new AiPrompt(1L, "CHAT", "sistema", "pregunta"), deltas::add))
            .isInstanceOf(AiUnavailableException.class).hasMessageContaining("dejó de enviar");
        assertThat(deltas).containsExactly("El pH ");
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2000);
    }

    @Test
    void failedStreamsReportTheProviderAsUnavailable() {
        server.reply(429, "{\"error\":\"rate\"}");
        assertThatThrownBy(() -> provider.stream(new AiPrompt(1L, "CHAT", "sistema", "pregunta"), d -> { }))
            .isInstanceOf(AiUnavailableException.class).hasMessageContaining("límite");
    }

    @Test
    void plainTextPromptsDoNotRequestJson() throws Exception {
        server.replyContent("gemini-3.5-flash", "Hola");
        provider.complete(new AiPrompt(1L, "CHAT", "sistema", "pregunta"));
        assertThat(mapper.readTree(server.requests().get(0)).has("response_format")).isFalse();
    }

    @Test
    void rateLimitsAndOutagesBecomeUnavailableErrors() {
        server.reply(429, "{\"error\":{\"message\":\"rate limit\"}}").reply(503, "{\"error\":{}}", "999")
            .reply(200, "{\"choices\":[{\"message\":{\"content\":\"\"},\"finish_reason\":\"length\"}]}");
        AiPrompt prompt = new AiPrompt(1L, "CHAT", "s", "u");
        assertThatThrownBy(() -> provider.complete(prompt)).isInstanceOf(AiUnavailableException.class).hasMessageContaining("límite");
        assertThatThrownBy(() -> provider.complete(prompt)).isInstanceOf(AiUnavailableException.class).hasMessageContaining("503");
        assertThatThrownBy(() -> provider.complete(prompt)).isInstanceOf(AiUnavailableException.class).hasMessageContaining("vacía");
    }

    @Test
    void retriesOnceWhenTheProviderAsksForAShortWait() {
        server.reply(429, "{\"error\":{\"message\":\"tokens per minute\"}}", "1").replyContent("openai/gpt-oss-120b", "Hola");
        long start = System.currentTimeMillis();
        AiAnswer answer = provider.complete(new AiPrompt(1L, "CHAT", "s", "u"));
        assertThat(answer.text()).isEqualTo("Hola");
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(1000);
        assertThat(server.requests()).hasSize(2);
    }

    @Test
    void doesNotWaitWhenTheLimitResetsLater() {
        server.reply(429, "{\"error\":{\"message\":\"daily quota\"}}", "3600");
        assertThatThrownBy(() -> provider.complete(new AiPrompt(1L, "CHAT", "s", "u"))).isInstanceOf(AiUnavailableException.class);
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void providerWithoutKeyIsNotAvailable() {
        var unconfigured = new OpenAiCompatibleProvider(
            new AppProperties.Provider("gemini", "Google Gemini", server.baseUrl(), "", "gemini-3.5-flash", 5, true, true, false, null), mapper);
        assertThat(unconfigured.isAvailable()).isFalse();
        assertThatThrownBy(() -> unconfigured.complete(new AiPrompt(1L, "CHAT", "s", "u"))).isInstanceOf(AiUnavailableException.class);
    }
}
