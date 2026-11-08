package com.aquasense.intelligence.agent.tools;

import com.aquasense.intelligence.agent.AgentContext;
import com.aquasense.intelligence.agent.AgentTool;
import com.aquasense.knowledge.Citation;
import com.aquasense.knowledge.GuidelineRetriever;
import com.aquasense.profile.ProfileService;
import com.aquasense.profile.WaterProfile;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * RAG tool of the agent: searches literal fragments of official guidelines that apply to the active profile.
 * The profile type is set by the server; the model only decides the query.
 */
@Component
public class SearchGuidelinesTool implements AgentTool {

    static final int LIMIT = 4;
    static final int MAX_QUERY = 200;

    private final GuidelineRetriever retriever;
    private final ProfileService profileService;

    public SearchGuidelinesTool(GuidelineRetriever retriever, ProfileService profileService) {
        this.retriever = retriever;
        this.profileService = profileService;
    }

    @Override
    public String name() {
        return "search_guidelines";
    }

    @Override
    public String description() {
        return "Searches literal fragments of official guidelines (EPA, WHO, CDC, FAO) that apply to the active water profile. "
            + "Use it to back recommendations or ranges with a source. It returns ids you can cite in citedSourceIds.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object",
            "properties", Map.of("query", Map.of("type", "string",
                "description", "What to search for, in Spanish. Examples: \"rango recomendado\", \"pH bajo corrosión\", \"frecuencia de medición\"")),
            "required", List.of("query"));
    }

    @Override
    public Object execute(AgentContext context, JsonNode arguments) {
        String query = arguments == null ? "" : arguments.path("query").asText("").trim();
        if (query.length() > MAX_QUERY) {
            query = query.substring(0, MAX_QUERY);
        }
        WaterProfile profile = profileService.activeProfile(context.userId());
        List<Citation> found = retriever.search(query.isEmpty() ? "rango recomendado" : query, profile.getType(), null, null, LIMIT);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profile.getType().label());
        if (found.isEmpty()) {
            result.put("coverage", retriever.coverage(profile.getType()));
        }
        result.put("fragments", found.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.id());
            row.put("organization", c.organization());
            row.put("document", c.title());
            row.put("section", c.section());
            row.put("literalQuote", c.quote());
            row.put("summary", c.summaryEs());
            row.put("legalStatus", c.legalStatus());
            return row;
        }).toList());
        return result;
    }

    @Override
    public Set<String> citableIds(Object result) {
        Set<String> ids = new LinkedHashSet<>();
        if (result instanceof Map<?, ?> map && map.get("fragments") instanceof List<?> rows) {
            rows.forEach(r -> ids.add(String.valueOf(((Map<?, ?>) r).get("id"))));
        }
        return ids;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Buscó en la biblioteca normativa: \"" + (arguments == null ? "" : arguments.path("query").asText("")) + "\"";
    }

    @Override
    public String summarize(Object result) {
        Set<String> ids = citableIds(result);
        return ids.isEmpty() ? "Sin fragmentos aplicables al perfil" : ids.size() + " fragmentos: " + String.join(", ", ids);
    }
}
