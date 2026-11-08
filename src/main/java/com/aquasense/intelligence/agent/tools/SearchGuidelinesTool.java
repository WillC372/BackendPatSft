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
 * Herramienta RAG del agente: busca fragmentos literales de normas oficiales aplicables al perfil activo.
 * El tipo de perfil lo fija el servidor; el modelo solo decide la consulta.
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
        return "Busca fragmentos literales de normas y guías oficiales (EPA, OMS, CDC, FAO) aplicables al perfil de agua activo. "
            + "Úsala para respaldar recomendaciones o rangos con una fuente. Devuelve ids que puedes citar en citedSourceIds.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object",
            "properties", Map.of("query", Map.of("type", "string",
                "description", "Qué buscar, en español. Ejemplos: \"rango recomendado\", \"pH bajo corrosión\", \"frecuencia de medición\"")),
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
        result.put("perfil", profile.getType().label());
        if (found.isEmpty()) {
            result.put("cobertura", retriever.coverage(profile.getType()));
        }
        result.put("fragmentos", found.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.id());
            row.put("organizacion", c.organization());
            row.put("documento", c.title());
            row.put("seccion", c.section());
            row.put("citaLiteral", c.quote());
            row.put("resumen", c.summaryEs());
            row.put("caracter", c.legalStatus());
            return row;
        }).toList());
        return result;
    }

    @Override
    public Set<String> citableIds(Object result) {
        Set<String> ids = new LinkedHashSet<>();
        if (result instanceof Map<?, ?> map && map.get("fragmentos") instanceof List<?> rows) {
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
