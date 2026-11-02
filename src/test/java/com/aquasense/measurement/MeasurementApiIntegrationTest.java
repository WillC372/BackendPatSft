package com.aquasense.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
class MeasurementApiIntegrationTest {

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

    private String device(String token, String type, String externalId) throws Exception {
        String body = mvc.perform(post("/api/v1/devices").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Medidor\",\"externalId\":\"" + externalId + "\",\"connectionType\":\""
                        + type + "\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions measure(String token, String deviceId, String ph,
                                                                       Instant at) throws Exception {
        String when = at == null ? "" : ",\"measuredAt\":\"" + at + "\"";
        return mvc.perform(post("/api/v1/measurements").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":\"" + deviceId + "\",\"profileId\":\"" + POOL + "\",\"ph\":" + ph + when + "}"));
    }

    @Test
    void simulatedDeviceProducesSimulatedMeasurementsAndRealDeviceRealOnes() throws Exception {
        String t = token("a@example.com");
        String sim = device(t, "SIMULATOR", "sim-1");
        String real = device(t, "BLUETOOTH", "AA:BB:CC:DD:EE:01");
        measure(t, sim, "7.4", null).andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("SIMULATED"));
        measure(t, real, "7.4", null).andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("REAL"))
                .andExpect(jsonPath("$.rangeStatus").value("OPTIMAL"))
                .andExpect(jsonPath("$.validationStatus").value("VALID"))
                .andExpect(jsonPath("$.disclaimer").value(org.hamcrest.Matchers.containsString("no demuestra")));
    }

    @Test
    void rejectsInvalidReadingsWithoutStoringThem() throws Exception {
        String t = token("a@example.com");
        String dev = device(t, "USB_SERIAL", "COM3");
        measure(t, dev, "15.2", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEASUREMENT_REJECTED"));
        measure(t, dev, "7.1", Instant.now().plusSeconds(3600)).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from measurements", Integer.class)).isZero();
    }

    @Test
    void flagsAbruptChangeAsSuspectButStoresIt() throws Exception {
        String t = token("a@example.com");
        String dev = device(t, "USB_SERIAL", "COM3");
        Instant now = Instant.now();
        measure(t, dev, "7.1", now.minusSeconds(60)).andExpect(status().isCreated());
        measure(t, dev, "4.2", now).andExpect(status().isCreated())
                .andExpect(jsonPath("$.validationStatus").value("SUSPECT"))
                .andExpect(jsonPath("$.rangeStatus").value("CRITICAL"));
    }

    @Test
    void measurementUpdatesDeviceLastSeenThroughEvent() throws Exception {
        String t = token("a@example.com");
        String dev = device(t, "BLUETOOTH", "AA:01");
        mvc.perform(get("/api/v1/devices/" + dev).header("Authorization", t))
                .andExpect(jsonPath("$.lastSeenAt").doesNotExist());
        measure(t, dev, "7.3", null).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/devices/" + dev).header("Authorization", t))
                .andExpect(jsonPath("$.lastSeenAt").exists());
    }

    @Test
    void usersCannotUseOrSeeEachOthersDevicesAndMeasurements() throws Exception {
        String ana = token("ana@example.com");
        String luis = token("luis@example.com");
        String anaDev = device(ana, "BLUETOOTH", "AA:01");
        String body = measure(ana, anaDev, "7.4", null).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(body).get("id").asText();

        measure(luis, anaDev, "7.4", null).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/measurements/" + id).header("Authorization", luis)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/devices/" + anaDev).header("Authorization", luis)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/measurements").header("Authorization", luis))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/measurements").header("Authorization", ana))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void filtersByPeriodAndSourceAndPaginates() throws Exception {
        String t = token("a@example.com");
        String real = device(t, "BLUETOOTH", "AA:01");
        String sim = device(t, "SIMULATOR", "sim");
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            measure(t, real, "7." + i, now.minusSeconds(3600L * (i + 1))).andExpect(status().isCreated());
        }
        measure(t, sim, "7.5", now.minusSeconds(60)).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/measurements?source=REAL&size=2&page=1").header("Authorization", t))
                .andExpect(jsonPath("$.totalItems").value(5)).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/v1/measurements?source=SIMULATED").header("Authorization", t))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/measurements?from=" + now.minusSeconds(7300) + "&to=" + now).header("Authorization", t))
                .andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get("/api/v1/measurements?from=" + now + "&to=" + now.minusSeconds(10)).header("Authorization", t))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/measurements/latest").header("Authorization", t))
                .andExpect(jsonPath("$.source").value("SIMULATED"));
    }

    @Test
    void duplicateDeviceAndUnlinkRules() throws Exception {
        String t = token("a@example.com");
        String dev = device(t, "BLUETOOTH", "AA:01");
        mvc.perform(post("/api/v1/devices").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Otro\",\"externalId\":\"AA:01\",\"connectionType\":\"BLUETOOTH\"}"))
                .andExpect(status().isConflict());
        measure(t, dev, "7.3", null).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/devices/" + dev).header("Authorization", t))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEVICE_HAS_MEASUREMENTS"));
        String empty = device(t, "USB_SERIAL", "COM9");
        mvc.perform(delete("/api/v1/devices/" + empty).header("Authorization", t)).andExpect(status().isNoContent());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/measurements")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/devices")).andExpect(status().isUnauthorized());
    }
}
