package com.aquasense.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntelligenceApiIntegrationTest {

    private static final String POOL = "00000000-0000-0000-0000-000000000002";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from users");
    }

    private String token(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"displayName\":\"U\",\"email\":\"" + email
                        + "\",\"password\":\"Clave1234\",\"confirmPassword\":\"Clave1234\"}"));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + email + "\",\"password\":\"Clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("accessToken").asText();
    }

    private String device(String token, String type) throws Exception {
        String body = mvc.perform(post("/api/v1/devices").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"M\",\"externalId\":\"" + type + "-1\",\"connectionType\":\"" + type + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private void measure(String token, String device, double ph, Instant at) throws Exception {
        mvc.perform(post("/api/v1/measurements").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":\"" + device + "\",\"profileId\":\"" + POOL + "\",\"ph\":" + ph
                        + ",\"measuredAt\":\"" + at + "\"}")).andExpect(status().isCreated());
    }

    private JsonNode analyze(String token, String body) throws Exception {
        String res = mvc.perform(post("/api/v1/analysis").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res);
    }

    @Test
    void reportsInsufficientDataWithoutInventingResults() throws Exception {
        String t = token("a@example.com");
        String dev = device(t, "BLUETOOTH");
        measure(t, dev, 7.4, Instant.now().minus(2, ChronoUnit.HOURS));
        JsonNode result = analyze(t, "{\"profileId\":\"" + POOL + "\"}");
        assertThat(result.at("/report/sufficientData").asBoolean()).isFalse();
        assertThat(result.at("/report/forecast/available").asBoolean()).isFalse();
        assertThat(result.at("/report/anomalies")).isEmpty();
        assertThat(result.at("/report/disclaimer").asText()).contains("no demuestra");
    }

    @Test
    void detectsAnomalyStoresAnalysisAndCreatesDeduplicatedAlerts() throws Exception {
        String t = token("a@example.com");
        String dev = device(t, "USB_SERIAL");
        Instant base = Instant.now().minus(30, ChronoUnit.HOURS);
        double[] values = {7.4, 7.5, 7.45, 7.5, 7.4, 7.5, 7.45, 7.4, 7.5, 5.8, 5.82, 5.8};
        for (int i = 0; i < values.length; i++) {
            measure(t, dev, values[i], base.plus(i * 2, ChronoUnit.HOURS));
        }
        JsonNode first = analyze(t, "{\"profileId\":\"" + POOL + "\"}");
        assertThat(first.at("/report/sufficientData").asBoolean()).isTrue();
        assertThat(first.at("/report/anomalies").size()).isGreaterThan(0);
        assertThat(first.get("alertsCreated").asInt()).isGreaterThan(0);
        String analysisId = first.get("analysisId").asText();

        JsonNode second = analyze(t, "{\"profileId\":\"" + POOL + "\"}");
        assertThat(second.get("alertsCreated").asInt()).isZero();

        mvc.perform(get("/api/v1/analysis/" + analysisId).header("Authorization", t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.measurementCount").value(12));
        mvc.perform(get("/api/v1/analysis/history").header("Authorization", t))
                .andExpect(jsonPath("$.length()").value(2));

        String alertsBody = mvc.perform(get("/api/v1/analysis/alerts").header("Authorization", t))
                .andReturn().getResponse().getContentAsString();
        JsonNode alerts = mapper.readTree(alertsBody);
        assertThat(alerts.size()).isEqualTo(first.get("alertsCreated").asInt());
        String alertId = alerts.get(0).get("id").asText();
        mvc.perform(post("/api/v1/analysis/alerts/" + alertId + "/acknowledge").header("Authorization", t))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acknowledgedAt").exists());
        mvc.perform(get("/api/v1/analysis/alerts").header("Authorization", t))
                .andExpect(jsonPath("$.length()").value(alerts.size() - 1));
    }

    @Test
    void simulatedDataCanBeExcludedAndIsDeclaredOtherwise() throws Exception {
        String t = token("a@example.com");
        String sim = device(t, "SIMULATOR");
        Instant base = Instant.now().minus(20, ChronoUnit.HOURS);
        for (int i = 0; i < 6; i++) {
            measure(t, sim, 7.4 + (i % 2) * 0.05, base.plus(i * 3, ChronoUnit.HOURS));
        }
        JsonNode with = analyze(t, "{\"profileId\":\"" + POOL + "\"}");
        assertThat(with.at("/report/simulatedCount").asInt()).isEqualTo(6);
        assertThat(with.at("/report/limitations").toString()).contains("SIMULACIÓN");
        JsonNode without = analyze(t, "{\"profileId\":\"" + POOL + "\",\"includeSimulated\":false}");
        assertThat(without.at("/report/measurementCount").asInt()).isZero();
        assertThat(without.at("/report/sufficientData").asBoolean()).isFalse();
    }

    @Test
    void scenarioSimulatorComparesWithLatestMeasurementAndIsMarkedHypothetical() throws Exception {
        String t = token("a@example.com");
        String dev = device(t, "BLUETOOTH");
        measure(t, dev, 7.4, Instant.now().minusSeconds(60));
        mvc.perform(post("/api/v1/analysis/scenario").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileId\":\"" + POOL + "\",\"delta\":-1.0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hypothetical").value(true))
                .andExpect(jsonPath("$.scenarioPh").value(6.4))
                .andExpect(jsonPath("$.baseline.status").value("OPTIMAL"))
                .andExpect(jsonPath("$.scenario.status").value("CRITICAL"))
                .andExpect(jsonPath("$.notice").value(org.hamcrest.Matchers.containsString("hipotético")));
        mvc.perform(post("/api/v1/analysis/scenario").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"profileId\":\"" + POOL + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SCENARIO_INCOMPLETE"));
    }

    @Test
    void analysesAndAlertsAreIsolatedBetweenUsers() throws Exception {
        String ana = token("ana@example.com");
        String luis = token("luis@example.com");
        String dev = device(ana, "BLUETOOTH");
        Instant base = Instant.now().minus(30, ChronoUnit.HOURS);
        for (int i = 0; i < 6; i++) {
            measure(ana, dev, i < 5 ? 7.4 : 5.5, base.plus(i * 3, ChronoUnit.HOURS));
        }
        JsonNode result = analyze(ana, "{\"profileId\":\"" + POOL + "\"}");
        String analysisId = result.get("analysisId").asText();
        mvc.perform(get("/api/v1/analysis/" + analysisId).header("Authorization", luis))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/analysis/alerts").header("Authorization", luis))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/analysis/history").header("Authorization", luis))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/analysis/history")).andExpect(status().isUnauthorized());
    }
}
