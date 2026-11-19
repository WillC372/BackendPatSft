package com.aquaassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Official USGS readings loaded into a place: one per hour, labeled OFFICIAL, without alerts, not repeated when
 * loaded again, and removable. (The station and its readings are test rows of the test database.)
 */
@SpringBootTest
@AutoConfigureMockMvc
class OfficialDataIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private JsonNode json(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void officialReadingsAreLoadedLabeledOncePerHourWithoutAlertsAndCanBeRemoved() throws Exception {
        String station = "USGS-T" + (System.nanoTime() % 1000000);
        jdbc.update("insert into reference_stations (id, agency, name, state, updated_at) values (?, 'USGS', 'RIO DE PRUEBA', 'TX', ?)",
            station, Timestamp.from(Instant.now()));
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(9));
        for (int q = 0; q < 9 * 24 * 4; q++) {
            // Every 15 minutes, with a jump of 2 units in the middle that would be an alarm if it were a new reading.
            double ph = q == 400 ? 5.0 : 7.5 + 0.1 * Math.sin(q / 20.0);
            jdbc.update("insert into reference_readings (station_id, measured_at, ph) values (?, ?, ?)", station,
                Timestamp.from(start.plus(Duration.ofMinutes(15L * q))), Math.round(ph * 100) / 100.0);
        }
        String token = json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"Docente\",\"email\":\"docente-" + UUID.randomUUID() + "@aquasense.test\",\"password\":\"Clave1234\"}")))
            .get("token").asText();
        String auth = "Bearer " + token;
        long place = json(mvc.perform(get("/api/v1/places").header("Authorization", auth))).get(0).get("id").asLong();

        JsonNode r = json(mvc.perform(post("/api/v1/places/" + place + "/official-data").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content("{\"stationId\":\"" + station + "\",\"days\":30}")).andExpect(status().isOk()));
        assertThat(r.get("added").asInt()).isBetween(9 * 24 - 2, 9 * 24 + 1);
        assertThat(r.get("stationName").asText()).isEqualTo("RIO DE PRUEBA");

        JsonNode page = json(mvc.perform(get("/api/v1/measurements").param("size", "5").header("Authorization", auth)));
        assertThat(page.get("content").get(0).get("source").asText()).isEqualTo("OFFICIAL");
        assertThat(json(mvc.perform(get("/api/v1/alerts").header("Authorization", auth)))).isEmpty();

        JsonNode again = json(mvc.perform(post("/api/v1/places/" + place + "/official-data").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content("{\"stationId\":\"" + station + "\"}")));
        assertThat(again.get("added").asInt()).isZero();

        JsonNode removed = json(mvc.perform(delete("/api/v1/places/" + place + "/official-data").header("Authorization", auth)));
        assertThat(removed.get("removed").asInt()).isEqualTo(r.get("added").asInt());

        mvc.perform(post("/api/v1/places/" + place + "/official-data").header("Authorization", auth)
            .contentType(MediaType.APPLICATION_JSON).content("{\"stationId\":\"USGS-NOEXISTE\"}")).andExpect(status().isNotFound());
    }
}
