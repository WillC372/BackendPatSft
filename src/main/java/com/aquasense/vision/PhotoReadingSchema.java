package com.aquasense.vision;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict JSON schema for photo reading. Verified with Gemini 3.5 Flash and Groq Qwen 3.8 (vision).
 */
public final class PhotoReadingSchema {

    public static final String NAME = "ph_photo_reading";
    public static final Map<String, Object> SCHEMA = build();

    private PhotoReadingSchema() {
    }

    private static Map<String, Object> build() {
        Map<String, Object> phNumber = Map.of("type", "number", "minimum", 0, "maximum", 14);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("readable", Map.of("type", "boolean"));
        properties.put("kind", Map.of("type", "string", "enum", names(PhotoKind.values())));
        properties.put("ph", phNumber);
        properties.put("phLow", phNumber);
        properties.put("phHigh", phNumber);
        properties.put("nearestReferenceValues", Map.of("type", "array", "maxItems", 3, "items", phNumber));
        properties.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));
        properties.put("referenceChartVisible", Map.of("type", "boolean"));
        properties.put("temperatureVisible", Map.of("type", "boolean"));
        properties.put("temperatureC", Map.of("type", "number"));
        properties.put("issues", Map.of("type", "array", "maxItems", 6,
            "items", Map.of("type", "string", "enum", names(PhotoIssue.values()))));
        properties.put("observations", Map.of("type", "string"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        return schema;
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
