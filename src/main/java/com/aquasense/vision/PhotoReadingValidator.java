package com.aquasense.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates the reading returned by the AI and adjusts it to the real precision of each instrument.
 * Models tend to claim high confidence and zero margin; here margins and limits are enforced:
 * strip with color chart ±0.5 and maximum confidence 0.7; without chart ±1.0 and 0.35; display ±0.05 and 0.9.
 */
@Component
public class PhotoReadingValidator {

    static final double STRIP_MIN_HALF_WIDTH = 0.5;
    static final double STRIP_NO_CHART_HALF_WIDTH = 1.0;
    static final double DISPLAY_MIN_HALF_WIDTH = 0.05;
    static final double STRIP_MAX_CONFIDENCE = 0.7;
    static final double STRIP_NO_CHART_MAX_CONFIDENCE = 0.35;
    static final double DISPLAY_MAX_CONFIDENCE = 0.9;
    static final double ISSUE_PENALTY = 0.1;
    private static final int MAX_OBSERVATIONS = 400;

    private final ObjectMapper mapper;

    public PhotoReadingValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public PhotoReading validate(String json, String provider, String model) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception ex) {
            throw new InvalidPhotoReadingException("La respuesta no es JSON.");
        }
        if (!root.isObject() || !root.path("readable").isBoolean() || !root.path("ph").isNumber()) {
            throw new InvalidPhotoReadingException("Faltan campos obligatorios.");
        }
        PhotoKind kind;
        try {
            kind = PhotoKind.valueOf(root.path("kind").asText());
        } catch (IllegalArgumentException ex) {
            throw new InvalidPhotoReadingException("Tipo de imagen no permitido.");
        }
        Set<PhotoIssue> issues = new LinkedHashSet<>();
        root.path("issues").forEach(node -> {
            try {
                issues.add(PhotoIssue.valueOf(node.asText()));
            } catch (IllegalArgumentException ignored) {
                // Unknown issue codes are dropped; the schema already restricts them.
            }
        });
        String observations = root.path("observations").asText("").trim();
        if (observations.length() > MAX_OBSERVATIONS) {
            observations = observations.substring(0, MAX_OBSERVATIONS - 1) + "…";
        }
        boolean chart = root.path("referenceChartVisible").asBoolean(false);
        boolean readable = root.path("readable").asBoolean() && kind != PhotoKind.OTHER && !issues.contains(PhotoIssue.NOT_A_PH_TEST);
        List<String> adjustments = new ArrayList<>();
        if (!readable) {
            return new PhotoReading(false, kind, null, null, null, 0, chart, null, List.copyOf(issues), observations,
                provider, model, adjustments);
        }
        double ph = root.path("ph").asDouble();
        if (ph < 0 || ph > 14) {
            throw new InvalidPhotoReadingException("pH fuera de 0-14.");
        }
        double low = Math.min(root.path("phLow").asDouble(ph), ph);
        double high = Math.max(root.path("phHigh").asDouble(ph), ph);
        for (JsonNode value : root.path("nearestReferenceValues")) {
            if (value.isNumber() && value.asDouble() >= 0 && value.asDouble() <= 14) {
                low = Math.min(low, value.asDouble());
                high = Math.max(high, value.asDouble());
            }
        }
        double confidence = Math.max(0, Math.min(1, root.path("confidence").asDouble(0)));
        double minHalfWidth;
        double maxConfidence;
        if (kind == PhotoKind.TEST_STRIP) {
            if (!chart) {
                issues.add(PhotoIssue.NO_REFERENCE_CHART);
            }
            minHalfWidth = chart ? STRIP_MIN_HALF_WIDTH : STRIP_NO_CHART_HALF_WIDTH;
            maxConfidence = chart ? STRIP_MAX_CONFIDENCE : STRIP_NO_CHART_MAX_CONFIDENCE;
            ph = Math.round(ph * 10) / 10.0;
        } else {
            minHalfWidth = DISPLAY_MIN_HALF_WIDTH;
            maxConfidence = DISPLAY_MAX_CONFIDENCE;
            ph = Math.round(ph * 100) / 100.0;
        }
        if (ph - low < minHalfWidth || high - ph < minHalfWidth) {
            double newLow = Math.min(low, ph - minHalfWidth);
            double newHigh = Math.max(high, ph + minHalfWidth);
            adjustments.add(String.format(Locale.US, "Margen ampliado a %.2f - %.2f por la precisión de %s.",
                clamp(newLow), clamp(newHigh), kind == PhotoKind.TEST_STRIP ? "una tira reactiva" : "la pantalla"));
            low = newLow;
            high = newHigh;
        }
        long penalized = issues.stream().filter(PhotoIssue::lowersConfidence).count();
        double capped = Math.max(0.1, Math.min(confidence, maxConfidence) - penalized * ISSUE_PENALTY);
        if (Math.abs(capped - confidence) > 0.001) {
            adjustments.add(String.format(Locale.US, "Confianza ajustada de %.2f a %.2f%s.", confidence, capped,
                penalized > 0 ? " por la calidad de la foto" : " por la precisión del método"));
        }
        Double temperature = kind == PhotoKind.METER_DISPLAY && root.path("temperatureVisible").asBoolean(false)
            && root.path("temperatureC").isNumber() ? root.path("temperatureC").asDouble() : null;
        return new PhotoReading(true, kind, ph, round(clamp(low)), round(clamp(high)), Math.round(capped * 100) / 100.0,
            chart, temperature, List.copyOf(issues), observations, provider, model, adjustments);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(14, value));
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    public static class InvalidPhotoReadingException extends RuntimeException {
        public InvalidPhotoReadingException(String message) {
            super(message);
        }
    }
}
