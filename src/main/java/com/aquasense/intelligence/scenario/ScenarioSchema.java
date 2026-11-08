package com.aquasense.intelligence.scenario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Esquema JSON estricto de la explicación de escenarios. {@code citedSourceIds} solo admite los ids entregados. */
final class ScenarioSchema {

    static final String NAME = "scenario_explanation";

    private ScenarioSchema() {
    }

    static Map<String, Object> forSources(List<String> ids) {
        Map<String, Object> text = Map.of("type", "string");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("headline", text);
        properties.put("explanation", text);
        properties.put("effects", Map.of("type", "array", "minItems", 1, "maxItems", 5, "items", object(Map.of(
            "area", text, "effect", text, "severity", Map.of("type", "string", "enum", List.of("INFO", "CAUTION", "CRITICAL"))),
            "area", "effect", "severity")));
        properties.put("earlySignals", Map.of("type", "array", "maxItems", 4, "items", text));
        properties.put("preventiveActions", Map.of("type", "array", "minItems", 1, "maxItems", 5, "items", text));
        properties.put("citedSourceIds", Map.of("type", "array", "maxItems", Math.max(1, Math.min(4, ids.size())),
            "items", ids.isEmpty() ? text : Map.of("type", "string", "enum", List.copyOf(ids))));
        return object(properties, properties.keySet().toArray(String[]::new));
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
