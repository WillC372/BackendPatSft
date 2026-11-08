package com.aquasense.intelligence.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;

/**
 * Tool the agent can invoke (Command pattern): wraps a read-only query with its name,
 * description and parameter schema, so the model decides when to use it.
 */
public interface AgentTool {

    String name();

    String description();

    Map<String, Object> parameters();

    Object execute(AgentContext context, JsonNode arguments);

    String label(JsonNode arguments);

    String summarize(Object result);

    /** Ids of the guideline fragments this result allows to cite. Only the RAG tool returns any. */
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
