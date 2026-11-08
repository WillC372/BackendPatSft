package com.aquasense.intelligence.diagnosis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Esquema JSON que se envía al proveedor para obligarlo a responder con salida estructurada.
 * Verificado con Gemini (endpoint compatible con OpenAI) y Groq en modo "strict".
 */
public final class DiagnosisSchema {

    public static final String NAME = "water_diagnosis";
    public static final Map<String, Object> SCHEMA = build(List.of());

    private DiagnosisSchema() {
    }

    /**
     * Esquema para una solicitud concreta: {@code citedSourceIds} solo admite los ids de los fragmentos entregados,
     * así el modelo no puede citar una norma que no recibió (el validador lo vuelve a comprobar).
     */
    public static Map<String, Object> forSources(List<String> sourceIds) {
        return sourceIds == null || sourceIds.isEmpty() ? SCHEMA : build(sourceIds);
    }

    private static Map<String, Object> build(List<String> sourceIds) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("riskLevel", enumOf("LOW", "MODERATE", "HIGH", "CRITICAL"));
        properties.put("summary", Map.of("type", "string"));
        properties.put("findings", array(object(Map.of(
            "title", Map.of("type", "string"),
            "evidence", Map.of("type", "string"),
            "severity", enumOf("INFO", "CAUTION", "CRITICAL")), "title", "evidence", "severity"), 1, 6));
        properties.put("probableCauses", array(object(Map.of(
            "cause", Map.of("type", "string"),
            "likelihood", enumOf("LOW", "MEDIUM", "HIGH"),
            "evidence", Map.of("type", "string")), "cause", "likelihood", "evidence"), 0, 5));
        properties.put("actions", array(object(Map.of(
            "action", Map.of("type", "string"),
            "priority", Map.of("type", "integer", "minimum", 1, "maximum", 3),
            "timeframe", Map.of("type", "string")), "action", "priority", "timeframe"), 1, 6));
        properties.put("dataGaps", array(Map.of("type", "string"), 0, 5));
        properties.put("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1));
        properties.put("nextMeasurementHours", Map.of("type", "integer", "minimum", 1, "maximum", 168));
        properties.put("citedSourceIds", array(sourceIds.isEmpty() ? Map.of("type", "string")
            : enumOf(sourceIds.toArray(String[]::new)), 0, Math.max(1, Math.min(4, sourceIds.size()))));
        return object(properties, properties.keySet().toArray(String[]::new));
    }

    private static Map<String, Object> enumOf(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private static Map<String, Object> array(Map<String, Object> items, int min, int max) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("type", "array");
        array.put("items", items);
        if (min > 0) {
            array.put("minItems", min);
        }
        array.put("maxItems", max);
        return array;
    }

    private static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", "object");
        object.put("additionalProperties", false);
        object.put("properties", properties);
        object.put("required", List.of(required));
        return object;
    }
}
