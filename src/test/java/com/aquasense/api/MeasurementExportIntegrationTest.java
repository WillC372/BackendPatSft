package com.aquasense.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The CSV export contains what the history table shows: the chosen period and filters. */
@SpringBootTest
@AutoConfigureMockMvc
class MeasurementExportIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void exportsTheChosenPeriodAndFilters() throws Exception {
        String token = mapper.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Usuario CSV\",\"email\":\"csv-" + UUID.randomUUID() + "@aquasense.test\",\"password\":\"Clave1234\"}"))
            .andReturn().getResponse().getContentAsString()).path("token").asText();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        measure(token, 7.1, now.minus(10, ChronoUnit.DAYS));
        measure(token, 5.2, now.minus(9, ChronoUnit.DAYS));
        measure(token, 7.3, now.minus(1, ChronoUnit.DAYS));

        String period = csv(token, "?from=" + now.minus(11, ChronoUnit.DAYS) + "&to=" + now.minus(8, ChronoUnit.DAYS));
        assertThat(period.lines().count()).isEqualTo(3);
        assertThat(period).contains("7.10").contains("5.20").doesNotContain("7.30");

        String critical = csv(token, "?from=" + now.minus(11, ChronoUnit.DAYS) + "&to=" + now + "&status=CRITICAL");
        assertThat(critical.lines().skip(1)).singleElement().asString().contains("5.20");

        assertThat(csv(token, "?days=7").lines().count()).isEqualTo(2);
        mvc.perform(get("/api/v1/measurements/export?from=" + now + "&to=" + now.minus(1, ChronoUnit.DAYS))
            .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }

    private void measure(String token, double ph, Instant at) throws Exception {
        mvc.perform(post("/api/v1/measurements").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"ph\":" + ph + ",\"measuredAt\":\"" + at + "\"}")).andExpect(status().isCreated());
    }

    private String csv(String token, String query) throws Exception {
        return mvc.perform(get("/api/v1/measurements/export" + query).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
