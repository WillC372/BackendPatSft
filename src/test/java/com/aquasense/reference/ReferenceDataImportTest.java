package com.aquasense.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Import of official data against a local server that answers with responses recorded from the real USGS API
 * (src/test/resources/usgs). Only the timestamps are moved to the present so the test does not age.
 */
@SpringBootTest(properties = "aquasense.reference.featured=USGS-01467200")
@AutoConfigureMockMvc
class ReferenceDataImportTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger FAIL_NEXT = new AtomicInteger();
    private static final HttpServer USGS = start();

    @DynamicPropertySource
    static void usgs(DynamicPropertyRegistry registry) {
        registry.add("aquasense.reference.base-url", () -> "http://127.0.0.1:" + USGS.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        USGS.stop(0);
    }

    @Autowired
    private ReferenceDataImporter importer;

    @Autowired
    private ReferenceReadingRepository readings;

    @Autowired
    private MockMvc mvc;

    @Test
    void importsLatestValuesAndTheSeriesOfFeaturedStationsWithoutDuplicates() throws Exception {
        ReferenceImport first = importer.refresh();
        assertThat(first.isSuccess()).isTrue();
        // The recorded response has 5 stations; one has no value (sensor without data) and is skipped.
        assertThat(first.getStations()).isEqualTo(4);
        assertThat(first.getReadings()).isEqualTo(6);
        ReferenceImport second = importer.refresh();
        assertThat(second.getReadings()).isZero();
        assertThat(readings.findByStationIdAndMeasuredAtAfterOrderByMeasuredAtAsc("USGS-01467200", Instant.EPOCH)).hasSize(6);

        String token = register();
        JsonNode summary = JSON.readTree(mvc.perform(get("/api/v1/reference/summary").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(summary.path("stationsReporting").asInt()).isEqualTo(4);
        assertThat(summary.path("minPh").decimalValue()).isEqualByComparingTo("4.9");
        assertThat(summary.path("maxPh").decimalValue()).isEqualByComparingTo("9.1");
        assertThat(summary.path("below").asLong()).isEqualTo(1);
        assertThat(summary.path("above").asLong()).isEqualTo(1);
        JsonNode station = summary.path("featured").get(0);
        assertThat(station.path("name").asText()).isEqualTo("Delaware River at Penn's Landing, Philadelphia, PA");
        assertThat(station.path("state").asText()).isEqualTo("Pennsylvania");
        assertThat(station.path("approvalStatus").asText()).isEqualTo("Provisional");

        JsonNode series = JSON.readTree(mvc.perform(get("/api/v1/reference/stations/USGS-01467200/readings")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(series.path("points")).hasSize(6);
        mvc.perform(get("/api/v1/reference/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void aFailedImportIsRecordedAndKeepsThePreviousData() {
        importer.refresh();
        FAIL_NEXT.set(1);
        ReferenceImport failed = importer.refresh();
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.getMessage()).contains("503");
        assertThat(readings.findByStationIdAndMeasuredAtAfterOrderByMeasuredAtAsc("USGS-01467200", Instant.EPOCH)).isNotEmpty();
    }

    private String register() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Usuario Oficial\",\"email\":\"oficial-" + UUID.randomUUID() + "@aquasense.test\",\"password\":\"Clave1234\"}"))
            .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).path("token").asText();
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/collections/latest-continuous/items", ex -> reply(ex, "latest-ph.json"));
            server.createContext("/collections/continuous/items", ex -> reply(ex, "series-01467200.json"));
            server.createContext("/collections/monitoring-locations/items/USGS-01467200", ex -> reply(ex, "location-01467200.json"));
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, String fixture) throws IOException {
        if (FAIL_NEXT.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        byte[] bytes = recent(fixture).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/geo+json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** The recorded response with its times moved to the last hours, 15 minutes apart as in the real data. */
    private static String recent(String fixture) throws IOException {
        try (InputStream in = ReferenceDataImportTest.class.getResourceAsStream("/usgs/" + fixture)) {
            JsonNode root = JSON.readTree(in);
            Instant base = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofHours(3));
            int i = 0;
            for (JsonNode f : root.path("features")) {
                ((ObjectNode) f.path("properties")).put("time", base.plus(Duration.ofMinutes(15L * i++)).toString());
            }
            return JSON.writeValueAsString(root);
        }
    }
}
