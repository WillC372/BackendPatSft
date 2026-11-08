package com.aquasense.intelligence.agent.tools;

import com.aquasense.alert.AlertResponse;
import com.aquasense.alert.AlertService;
import com.aquasense.intelligence.agent.AgentContext;
import com.aquasense.intelligence.agent.AgentTool;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AlertsTool implements AgentTool {

    private final AlertService alertService;

    public AlertsTool(AlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public String name() {
        return "get_alerts";
    }

    @Override
    public String description() {
        return "Lista las alertas más recientes generadas por el sistema (severidad, título, explicación, fecha y si fueron leídas).";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of("limit", AgentTool.intParam("Máximo de alertas", 1, 15)));
    }

    @Override
    public Object execute(AgentContext context, JsonNode arguments) {
        int limit = Math.max(1, Math.min(AgentTool.intArg(arguments, "limit", 10), 15));
        List<AlertResponse> alerts = alertService.list(context.userId(), false, limit);
        return Map.of("total", alerts.size(), "alertas", alerts.stream().map(a -> Map.of("fecha",
            AgentFormats.DATE.format(a.createdAt()), "severidad", a.severity(), "titulo", a.title(), "detalle", a.message(),
            "leida", a.read())).toList());
    }

    @Override
    public String label(JsonNode arguments) {
        return "Revisó las alertas recientes";
    }

    @Override
    public String summarize(Object result) {
        return ((Map<?, ?>) result).get("total") + " alertas";
    }
}
