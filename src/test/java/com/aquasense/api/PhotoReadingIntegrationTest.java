package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Lectura por foto con dos proveedores de visión falsos: el primero lee 6.0 y el segundo 7.0.
 * Comprueba la segunda opinión, el ajuste por discrepancia y el guardado con origen PHOTO.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PhotoReadingIntegrationTest {

    private static final FakeAiServer FIRST;
    private static final FakeAiServer SECOND;

    static {
        try {
            FIRST = new FakeAiServer();
            SECOND = new FakeAiServer();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void providers(DynamicPropertyRegistry registry) {
        register(registry, 0, "Google Gemini", FIRST, "gemini-3.5-flash");
        register(registry, 1, "Groq", SECOND, "qwen/qwen3.8-27b");
        registry.add("aquasense.ai.daily-quota-free", () -> 20);
    }

    private static void register(DynamicPropertyRegistry registry, int i, String label, FakeAiServer server, String model) {
        String p = "aquasense.ai.providers[" + i + "].";
        registry.add(p + "id", () -> "p" + i);
        registry.add(p + "label", () -> label);
        registry.add(p + "base-url", server::baseUrl);
        registry.add(p + "api-key", () -> "fake");
        registry.add(p + "model", () -> model);
        registry.add(p + "timeout-seconds", () -> 5);
        registry.add(p + "vision", () -> true);
    }

    @AfterAll
    static void stop() {
        FIRST.close();
        SECOND.close();
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String reading(String kind, boolean readable, double ph) {
        return "{\"readable\":" + readable + ",\"kind\":\"" + kind + "\",\"ph\":" + ph + ",\"phLow\":" + ph + ",\"phHigh\":" + ph
            + ",\"nearestReferenceValues\":[" + ph + "],\"confidence\":0.95,\"referenceChartVisible\":true,"
            + "\"temperatureVisible\":false,\"temperatureC\":0,\"issues\":[],\"observations\":\"Comparación con la carta.\"}";
    }

    @Test
    void stripReadingUsesSecondOpinionAndSavesAsPhotoSource() throws Exception {
        String token = register();
        MockMultipartFile photo = new MockMultipartFile("file", "tira.png", "image/png",
            com.aquasense.support.TestImages.png(1600, 1200));

        FIRST.replyContent("gemini-3.5-flash", reading("TEST_STRIP", true, 6.0));
        SECOND.replyContent("qwen/qwen3.8-27b", reading("TEST_STRIP", true, 7.0));
        JsonNode result = json(mvc.perform(multipart("/api/v1/vision/ph-reading").file(photo).param("kind", "TEST_STRIP")
            .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("agreement").asText()).isEqualTo("DISAGREE");
        assertThat(result.path("ph").asDouble()).isEqualTo(6.5);
        assertThat(result.path("confidence").asDouble()).isLessThanOrEqualTo(0.35);
        assertThat(result.path("opinions").size()).isEqualTo(2);
        assertThat(result.path("phLow").asDouble()).isLessThanOrEqualTo(5.5);
        assertThat(FIRST.requests().get(0)).contains("image_url").contains("data:image/jpeg;base64,");

        JsonNode saved = json(mvc.perform(post("/api/v1/vision/ph-reading/" + result.path("pendingId").asText() + "/save")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ph\":6.0,\"note\":\"piscina\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(saved.path("source").asText()).isEqualTo("PHOTO");
        assertThat(saved.path("ph").asDouble()).isEqualTo(6.0);
        assertThat(saved.path("note").asText()).contains("corregido por el usuario").contains("piscina");
        assertThat(saved.path("validationStatus").asText()).isEqualTo("WARNING");

        mvc.perform(post("/api/v1/vision/ph-reading/" + result.path("pendingId").asText() + "/save")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void unrelatedPhotoIsNotSavableAndMeterDisplaySkipsSecondOpinion() throws Exception {
        String token = register();
        MockMultipartFile photo = new MockMultipartFile("file", "x.png", "image/png", com.aquasense.support.TestImages.png(800, 600));
        FIRST.replyContent("gemini-3.5-flash", reading("OTHER", false, 0));
        JsonNode unreadable = json(mvc.perform(multipart("/api/v1/vision/ph-reading").file(photo)
            .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        assertThat(unreadable.path("readable").asBoolean()).isFalse();
        assertThat(unreadable.path("pendingId").isNull()).isTrue();

        photo = new MockMultipartFile("file", "medidor.png", "image/png", com.aquasense.support.TestImages.png(900, 700));
        int before = SECOND.requests().size();
        FIRST.replyContent("gemini-3.5-flash", reading("METER_DISPLAY", true, 7.42));
        JsonNode meter = json(mvc.perform(multipart("/api/v1/vision/ph-reading").file(photo)
            .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        assertThat(meter.path("ph").asDouble()).isEqualTo(7.42);
        assertThat(meter.path("agreement").asText()).isEqualTo("SINGLE");
        assertThat(SECOND.requests()).hasSize(before);
    }

    private String register() throws Exception {
        String email = "v" + UUID.randomUUID().toString().substring(0, 8) + "@aquasense.test";
        return json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Prueba\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andReturn().getResponse().getContentAsString()).path("token").asText();
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }
}
