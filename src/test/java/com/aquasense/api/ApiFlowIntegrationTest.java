package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquasense.device.connection.DeviceConnection;
import com.aquasense.device.connection.DeviceState;
import com.aquasense.measurement.MeasurementPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "aquasense.security.admin-email=admin-flow@aquasense.test")
@AutoConfigureMockMvc
class ApiFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DeviceConnection connection;

    @Autowired
    private MeasurementPublisher publisher;

    @AfterEach
    void releaseDevice() {
        if (connection.currentState() != DeviceState.DISCONNECTED) {
            connection.disconnect();
        }
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void registerValidatesInput() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"\",\"email\":\"no-es-correo\",\"password\":\"123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.email").exists())
            .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void loginRejectsWrongPasswordAndAcceptsValidCredentials() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Incorrecta1\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"Clave1234\",\"rememberMe\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
    }

    @Test
    void manualMeasurementsAreClassifiedAndIsolatedPerUser() throws Exception {
        String alice = register(uniqueEmail());
        String bob = register(uniqueEmail());

        JsonNode created = json(mvc.perform(post("/api/v1/measurements").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ph\":6.2,\"note\":\"grifo cocina\"}"))
            .andExpect(status().isCreated()).andReturn());
        assertThat(created.get("source").asText()).isEqualTo("MANUAL");
        assertThat(created.get("status").asText()).isEqualTo("CAUTION");
        long id = created.get("id").asLong();

        mvc.perform(get("/api/v1/measurements/" + id).header("Authorization", bearer(bob))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/measurements/" + id).header("Authorization", bearer(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/measurements").header("Authorization", bearer(bob)))
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/measurements").header("Authorization", bearer(alice)))
            .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/v1/alerts").header("Authorization", bearer(alice)))
            .andExpect(jsonPath("$[0].title").value("pH fuera del rango ideal"));
        assertThat(publisher.listenerCount()).isGreaterThanOrEqualTo(1);

        mvc.perform(post("/api/v1/measurements").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ph\":15}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void profilesCanBeDuplicatedEditedAndActivated() throws Exception {
        String token = register(uniqueEmail());
        JsonNode profiles = json(mvc.perform(get("/api/v1/profiles").header("Authorization", bearer(token))).andReturn());
        assertThat(profiles.size()).isEqualTo(5);
        long poolId = 0;
        for (JsonNode p : profiles) {
            if (p.get("type").asText().equals("POOL")) {
                poolId = p.get("id").asLong();
            }
        }
        mvc.perform(put("/api/v1/profiles/" + poolId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(profileBody("Hack", 1, 2, 0, 3)))
            .andExpect(status().isBadRequest());

        JsonNode copy = json(mvc.perform(post("/api/v1/profiles/" + poolId + "/duplicate").header("Authorization", bearer(token)))
            .andExpect(status().isCreated()).andReturn());
        long copyId = copy.get("id").asLong();
        mvc.perform(put("/api/v1/profiles/" + copyId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(profileBody("Piscina climatizada", 7.3, 7.6, 7.1, 7.9)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.idealMin").value(7.3));
        mvc.perform(post("/api/v1/profiles/" + copyId + "/activate").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/v1/dashboard").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.activeProfile.name").value("Piscina climatizada"));
    }

    @Test
    void simulatorReadingsAreLabelledAndSavedOnRequest() throws Exception {
        String token = register(uniqueEmail());
        mvc.perform(post("/api/v1/devices/measure").header("Authorization", bearer(token))).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/devices/scan").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$[0].simulated").value(true));
        // An arbitrary server path is never opened as a serial port.
        for (String path : new String[] {"/dev/sda", "/etc/passwd", "../../dev/mem"}) {
            mvc.perform(post("/api/v1/devices/connect").header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"protocol\":\"ATLAS_EZO\",\"port\":\"" + path + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no existe")));
        }
        mvc.perform(post("/api/v1/devices/connect").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"protocol\":\"SIMULATOR\"}"))
            .andExpect(jsonPath("$.state").value("CONNECTED")).andExpect(jsonPath("$.simulated").value(true));

        String other = register(uniqueEmail());
        mvc.perform(post("/api/v1/devices/disconnect").header("Authorization", bearer(other))).andExpect(status().isConflict());

        JsonNode preview = json(mvc.perform(post("/api/v1/devices/measure").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andReturn());
        assertThat(preview.get("source").asText()).isEqualTo("SIMULATED");
        assertThat(preview.get("saved").asBoolean()).isFalse();
        String pendingId = preview.get("pendingId").asText();

        mvc.perform(post("/api/v1/devices/readings/" + pendingId + "/save").header("Authorization", bearer(other)))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/devices/readings/" + pendingId + "/save").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"prueba\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("SIMULATED"))
            .andExpect(jsonPath("$.deviceName").value("Simulador AquaSense"));

        mvc.perform(post("/api/v1/devices/calibrate").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"point\":\"MID\"}"))
            .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/v1/devices/disconnect").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.state").value("DISCONNECTED"));
    }

    @Test
    void intelligenceWorksWithoutGenerativeAiAndRefusesUnfoundedForecasts() throws Exception {
        String token = register(uniqueEmail());
        mvc.perform(get("/api/v1/intelligence/overview").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.hasData").value(false));
        for (double ph : new double[] {7.1, 7.2, 7.15, 7.3}) {
            mvc.perform(post("/api/v1/measurements").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ph\":" + ph + "}")).andExpect(status().isCreated());
        }
        mvc.perform(get("/api/v1/intelligence/forecast").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.forecast.available").value(false));
        mvc.perform(post("/api/v1/intelligence/scenario").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"hypotheticalPh\":6.0}"))
            .andExpect(jsonPath("$.hypothetical.nature").value("ACIDIC"));
        mvc.perform(post("/api/v1/intelligence/scenario/explain").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"hypotheticalPh\":6.0}"))
            .andExpect(jsonPath("$.source").value("RULES"))
            .andExpect(jsonPath("$.hydrogenFactor").value(20.0))
            .andExpect(jsonPath("$.citations.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/v1/intelligence/deep/forecast").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.trained").value(false)).andExpect(jsonPath("$.reason").exists())
            .andExpect(jsonPath("$.data.measurements").value(4));
        mvc.perform(get("/api/v1/intelligence/ai-usage").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.totals.calls").value(0)).andExpect(jsonPath("$.billing").value("FREE"));
        mvc.perform(post("/api/v1/intelligence/conclusion").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.source").value("RULES"));
        mvc.perform(post("/api/v1/intelligence/chat").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Como está mi agua?\"}"))
            .andExpect(status().isServiceUnavailable());
        var streamed = mvc.perform(post("/api/v1/intelligence/chat/stream").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Como está mi agua?\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        for (int i = 0; i < 100 && !streamed.getResponse().getContentAsString().contains("event:error"); i++) {
            Thread.sleep(100);
        }
        assertThat(streamed.getResponse().getContentAsString())
            .contains("event:error").contains("\"status\":503");
        mvc.perform(get("/api/v1/intelligence/ml/insights").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.trained").value(false)).andExpect(jsonPath("$.reason").exists());
        mvc.perform(get("/api/v1/intelligence/ai-status").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.maxAnalysisDays").value(30));
        mvc.perform(get("/api/v1/reports").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.measurementCount").value(4))
            .andExpect(jsonPath("$.sections[0].key").value("summary"));
        mvc.perform(get("/api/v1/measurements/export").header("Authorization", bearer(token)))
            .andExpect(status().isOk());
    }

    @Test
    void knowledgeLibraryBacksRecommendationsWithVerbatimSources() throws Exception {
        String token = register(uniqueEmail());
        mvc.perform(get("/api/v1/knowledge/sources")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/knowledge/sources").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.sources.length()").value(4))
            .andExpect(jsonPath("$.coverage.AQUARIUM").exists());
        mvc.perform(get("/api/v1/knowledge/search").param("q", "cierre de la piscina").param("profileType", "POOL")
                .header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.results[0].id").value("mahc-imminent-hazard"))
            .andExpect(jsonPath("$.results[0].organization").value("CDC (Estados Unidos)"));
        mvc.perform(get("/api/v1/knowledge/search").param("q", " ").header("Authorization", bearer(token)))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/knowledge/chunks/fao-table1").header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.quote").value("pH Normal Range 6.5 – 8.4"));
        mvc.perform(get("/api/v1/knowledge/chunks/no-existe").header("Authorization", bearer(token)))
            .andExpect(status().isNotFound());

        JsonNode profiles = json(mvc.perform(get("/api/v1/profiles").header("Authorization", bearer(token))).andReturn());
        long poolId = 0;
        for (JsonNode p : profiles) {
            if (p.get("type").asText().equals("POOL")) {
                assertThat(p.get("idealMin").asDouble()).isEqualTo(7.0);
                assertThat(p.get("cautionMin").asDouble()).isEqualTo(6.5);
                poolId = p.get("id").asLong();
            }
            if (p.get("type").asText().equals("IRRIGATION")) {
                assertThat(p.get("idealMin").asDouble()).isEqualTo(6.5);
                assertThat(p.get("idealMax").asDouble()).isEqualTo(8.4);
            }
        }
        mvc.perform(post("/api/v1/profiles/" + poolId + "/activate").header("Authorization", bearer(token)))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/measurements").header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content("{\"ph\":6.3}")).andExpect(status().isCreated());
        JsonNode rec = json(mvc.perform(get("/api/v1/intelligence/recommendations").header("Authorization", bearer(token)))
            .andReturn());
        assertThat(rec.path("sources").size()).isBetween(1, 3);
        rec.path("sources").forEach(c -> assertThat(c.path("sourceId").asText()).isEqualTo("cdc-mahc-2024"));
        assertThat(rec.path("sources").toString()).contains("mahc-imminent-hazard");
    }

    @Test
    void photoReadingRequiresAVisionProvider() throws Exception {
        String token = register(uniqueEmail());
        var photo = new org.springframework.mock.web.MockMultipartFile("file", "t.png", "image/png",
            com.aquasense.support.TestImages.png(400, 300));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/vision/ph-reading")
                .file(photo).header("Authorization", bearer(token)))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void browserReadMetersSaveRealMeasurements() throws Exception {
        String token = register(uniqueEmail());
        MvcResult saved = mvc.perform(post("/api/v1/devices/browser-readings").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ph\":7.034,\"temperatureC\":24.5,\"protocol\":\"TEXT_METER\",\"deviceName\":\"Hanna HI2211\"}"))
            .andExpect(status().isCreated()).andReturn();
        JsonNode m = json(saved);
        assertThat(m.path("ph").asDouble()).isEqualTo(7.03);
        assertThat(m.path("source").asText()).isEqualTo("REAL");
        JsonNode devices = json(mvc.perform(get("/api/v1/devices").header("Authorization", bearer(token))).andReturn());
        assertThat(devices.get(0).path("name").asText()).isEqualTo("Hanna HI2211");
        assertThat(devices.get(0).path("simulated").asBoolean()).isFalse();
        mvc.perform(post("/api/v1/devices/browser-readings").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"ph\":15,\"protocol\":\"TEXT_METER\",\"deviceName\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/devices/browser-readings").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"ph\":7,\"protocol\":\"SIMULATOR\",\"deviceName\":\"x\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownRoutesAndWrongMethodsAreClientErrors() throws Exception {
        String token = register(uniqueEmail());
        mvc.perform(get("/api/v1/no-such-route").header("Authorization", bearer(token))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/users/me/settings").header("Authorization", bearer(token)))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void logoutRevokesExistingTokens() throws Exception {
        String token = register(uniqueEmail());
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        String user = register(uniqueEmail());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(user))).andExpect(status().isForbidden());
        // Registering with the ADMIN_EMAIL address does not grant the role: it must be confirmed from that email.
        MvcResult claimed = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Intruso\",\"email\":\"admin-flow@aquasense.test\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn();
        assertThat(json(claimed).path("user").path("role").asText()).isEqualTo("USER");
        assertThat(json(claimed).path("notice").asText()).contains("administrador");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(json(claimed).path("token").asText())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/verify-admin").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"inventado\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void passwordResetRequestDoesNotRevealAccounts() throws Exception {
        // Without SMTP and without development mode, recovery is not available, the same for any email.
        String existing = uniqueEmail();
        register(existing);
        for (String email : new String[] {"nadie@aquasense.test", existing}) {
            mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no está disponible")));
        }
        mvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"inválido\",\"newPassword\":\"NuevaClave1\"}"))
            .andExpect(status().isBadRequest());
    }

    private String register(String email) throws Exception {
        return registerWithEmail(email);
    }

    private String registerWithEmail(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Usuario Prueba\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}"))
            .andExpect(status().isCreated()).andReturn();
        return json(result).get("token").asText();
    }

    private String registerOrLogin(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"Admin\",\"email\":\"" + email + "\",\"password\":\"Clave1234\"}")).andReturn();
        if (result.getResponse().getStatus() == 409) {
            result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Clave1234\"}")).andExpect(status().isOk()).andReturn();
        }
        return json(result).get("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@aquasense.test";
    }

    private static String profileBody(String name, double idealMin, double idealMax, double cautionMin, double cautionMax) {
        return String.format(java.util.Locale.US,
            "{\"name\":\"%s\",\"idealMin\":%.2f,\"idealMax\":%.2f,\"cautionMin\":%.2f,\"cautionMax\":%.2f,"
                + "\"description\":\"desc\",\"source\":\"fuente\"}", name, idealMin, idealMax, cautionMin, cautionMax);
    }
}
