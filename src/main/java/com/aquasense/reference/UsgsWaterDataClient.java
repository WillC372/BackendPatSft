package com.aquasense.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapter for the USGS Water Data OGC API (https://api.waterdata.usgs.gov/ogcapi/v0). It turns the GeoJSON responses
 * into plain records. Parameter 00400 is pH; statistic 00011 is the instantaneous value. Values are provisional until
 * the USGS reviews them, and that status is kept with each value.
 */
@Component
public class UsgsWaterDataClient {

    static final String PH = "00400";
    static final String INSTANTANEOUS = "00011";
    private static final int MAX_PAGES = 10;

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public UsgsWaterDataClient(ObjectMapper mapper,
                               @Value("${aquasense.reference.base-url:https://api.waterdata.usgs.gov/ogcapi/v0}") String baseUrl) {
        this.mapper = mapper;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.http = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public record PhValue(String stationId, Instant time, BigDecimal ph, String approval, Double latitude, Double longitude) {
    }

    public record Location(String id, String name, String state, String siteType, Double latitude, Double longitude) {
    }

    /** Latest pH value of every station that reported after {@code since}. */
    public List<PhValue> latestPh(Instant since) throws IOException {
        return features("/collections/latest-continuous/items?f=json&parameter_code=" + PH + "&datetime="
            + encode(since.truncatedTo(ChronoUnit.SECONDS) + "/..") + "&limit=1000");
    }

    /** Readings of one station after {@code since}, oldest first. */
    public List<PhValue> series(String stationId, Instant since) throws IOException {
        return features("/collections/continuous/items?f=json&monitoring_location_id=" + encode(stationId) + "&parameter_code=" + PH
            + "&datetime=" + encode(since.truncatedTo(ChronoUnit.SECONDS) + "/..") + "&limit=2000");
    }

    public Optional<Location> location(String stationId) throws IOException {
        JsonNode root = get(baseUrl + "/collections/monitoring-locations/items/" + encode(stationId) + "?f=json");
        if (root == null) {
            return Optional.empty();
        }
        JsonNode p = root.path("properties");
        JsonNode c = root.path("geometry").path("coordinates");
        return Optional.of(new Location(stationId, text(p, "monitoring_location_name"), text(p, "state_name"), text(p, "site_type"),
            c.size() == 2 ? c.get(1).asDouble() : null, c.size() == 2 ? c.get(0).asDouble() : null));
    }

    /** Follows the "next" links of the paged response. */
    private List<PhValue> features(String path) throws IOException {
        List<PhValue> values = new ArrayList<>();
        String url = baseUrl + path;
        for (int page = 0; url != null && page < MAX_PAGES; page++) {
            JsonNode root = get(url);
            if (root == null) {
                break;
            }
            for (JsonNode f : root.path("features")) {
                JsonNode p = f.path("properties");
                String raw = p.path("value").asText(null);
                if (raw == null || raw.isBlank() || !INSTANTANEOUS.equals(p.path("statistic_id").asText(INSTANTANEOUS))) {
                    continue;
                }
                BigDecimal ph;
                try {
                    ph = new BigDecimal(raw.trim());
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (ph.compareTo(BigDecimal.ZERO) < 0 || ph.compareTo(BigDecimal.valueOf(14)) > 0) {
                    continue;
                }
                JsonNode c = f.path("geometry").path("coordinates");
                values.add(new PhValue(p.path("monitoring_location_id").asText(), OffsetDateTime.parse(p.path("time").asText()).toInstant(),
                    ph, text(p, "approval_status"), c.size() == 2 ? c.get(1).asDouble() : null, c.size() == 2 ? c.get(0).asDouble() : null));
            }
            url = null;
            for (JsonNode link : root.path("links")) {
                if ("next".equals(link.path("rel").asText())) {
                    url = link.path("href").asText(null);
                }
            }
        }
        return values;
    }

    private JsonNode get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
            .header("Accept", "application/geo+json, application/json").header("User-Agent", "AquaSense-AI/1.0").GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("USGS request interrupted", ex);
        }
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() >= 400) {
            throw new IOException("USGS answered " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
