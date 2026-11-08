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
        properties.put("title", Map.of("type", "string", "description", "Short title of the report, in Spanish"));
        properties.put("executiveSummary", Map.of("type", "string", "description", "Executive summary of 3 to 5 sentences that answers the goal, in Spanish"));
        properties.put("keyMetrics", Map.of("type", "array", "description", "Between 2 and 6 key figures", "items", Map.of("type", "object",
            "properties", Map.of("label", text, "value", text), "required", List.of("label", "value"))));
        properties.put("sections", Map.of("type", "array", "description", "Between 2 and 7 sections", "items", Map.of("type", "object",
            "properties", Map.of("heading", text, "body", text, "chart", Map.of("type", "string",
                "description", "Chart that goes with the section", "enum",
                Arrays.stream(AgentReport.ChartKind.values()).map(Enum::name).toList())),
            "required", List.of("heading", "body", "chart"))));
        properties.put("recommendations", Map.of("type", "array", "description", "Between 1 and 6 actions", "items", Map.of("type", "object",
            "properties", Map.of("action", text, "priority", Map.of("type", "integer", "minimum", 1, "maximum", 3), "rationale", text),
            "required", List.of("action", "priority", "rationale"))));
        properties.put("limitations", Map.of("type", "array", "items", text, "description", "Limits of the data or of the analysis"));
        properties.put("citedSourceIds", Map.of("type", "array", "items", text, "description",
            "Ids of fragments returned by search_guidelines that the report relies on. Empty if you used no guidelines."));
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()));
    }
}
