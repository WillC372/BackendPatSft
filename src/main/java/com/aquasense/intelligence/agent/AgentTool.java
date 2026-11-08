package com.aquasense.intelligence.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;

/**
 * Herramienta que el agente puede invocar (patrón Command): encapsula una consulta de solo lectura con su nombre,
 * descripción y esquema de parámetros, para que el modelo decida cuándo usarla.
 */
public interface AgentTool {

    String name();

    String description();

    Map<String, Object> parameters();

    Object execute(AgentContext context, JsonNode arguments);

    String label(JsonNode arguments);

    String summarize(Object result);

    /** Ids de fragmentos normativos que este resultado permite citar. Solo la herramienta RAG devuelve alguno. */
    default Set<String> citableIds(Object result) {
        return Set.of();
    }

    static int intArg(JsonNode args, String field, int fallback) {
        JsonNode node = args == null ? null : args.get(field);
        return node != null && node.canConvertToInt() ? node.asInt() : fallback;
    }

    static Map<String, Object> intParam(String description, int min, int max) {
        return Map.of("type", "integer", "description", description, "minimum", min, "maximum", max);
    }
}
