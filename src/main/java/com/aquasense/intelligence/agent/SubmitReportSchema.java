package com.aquasense.intelligence.agent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SubmitReportSchema {

    public static final String TOOL_NAME = "submit_report";
    public static final String DESCRIPTION = "Entrega el informe final. Llámala una sola vez, cuando ya consultaste los datos necesarios. "
        + "Todas las cifras deben provenir de los resultados de las herramientas.";

    private SubmitReportSchema() {
    }

    public static Map<String, Object> parameters() {
        Map<String, Object> text = Map.of("type", "string");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of("type", "string", "description", "Título breve del informe"));
        properties.put("executiveSummary", Map.of("type", "string", "description", "Resumen ejecutivo de 3 a 5 frases que responde al objetivo"));
        properties.put("keyMetrics", Map.of("type", "array", "description", "Entre 2 y 6 cifras clave", "items", Map.of("type", "object",
            "properties", Map.of("label", text, "value", text), "required", List.of("label", "value"))));
        properties.put("sections", Map.of("type", "array", "description", "Entre 2 y 7 secciones", "items", Map.of("type", "object",
            "properties", Map.of("heading", text, "body", text, "chart", Map.of("type", "string",
                "description", "Gráfica que acompaña la sección", "enum",
                Arrays.stream(AgentReport.ChartKind.values()).map(Enum::name).toList())),
            "required", List.of("heading", "body", "chart"))));
        properties.put("recommendations", Map.of("type", "array", "description", "Entre 1 y 6 acciones", "items", Map.of("type", "object",
            "properties", Map.of("action", text, "priority", Map.of("type", "integer", "minimum", 1, "maximum", 3), "rationale", text),
            "required", List.of("action", "priority", "rationale"))));
        properties.put("limitations", Map.of("type", "array", "items", text, "description", "Límites de los datos o del análisis"));
        properties.put("citedSourceIds", Map.of("type", "array", "items", text, "description",
            "Ids de fragmentos devueltos por search_guidelines en los que se apoya el informe. Vacío si no usaste normas."));
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()));
    }
}
