package com.aquasense.intelligence.agent.tools;

import com.aquasense.intelligence.agent.AgentContext;
import com.aquasense.intelligence.agent.AgentTool;
import com.aquasense.intelligence.diagnosis.DiagnosisService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LatestDiagnosisTool implements AgentTool {

    private final DiagnosisService diagnosisService;

    public LatestDiagnosisTool(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @Override
    public String name() {
        return "get_latest_diagnosis";
    }

    @Override
    public String description() {
        return "Devuelve el último diagnóstico guardado (riesgo, resumen, acciones y fecha), si existe.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Object execute(AgentContext context, JsonNode arguments) {
        return diagnosisService.latest(context.userId()).<Object>map(d -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("existe", true);
            result.put("fecha", AgentFormats.DATE.format(d.createdAt()));
            result.put("riesgo", d.riskLabel());
            result.put("resumen", d.summary());
            result.put("acciones", d.actions().stream().map(a -> a.action()).toList());
            result.put("origen", d.source());
            return result;
        }).orElse(Map.of("existe", false));
    }

    @Override
    public String label(JsonNode arguments) {
        return "Consultó el último diagnóstico";
    }

    @Override
    public String summarize(Object result) {
        Map<?, ?> map = (Map<?, ?>) result;
        return Boolean.TRUE.equals(map.get("existe")) ? "riesgo " + map.get("riesgo") + " (" + map.get("fecha") + ")" : "sin diagnósticos";
    }
}
