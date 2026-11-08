package com.aquasense.intelligence.agent.tools;

import com.aquasense.intelligence.agent.AgentContext;
import com.aquasense.intelligence.agent.AgentTool;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MeasurementsTool implements AgentTool {

    static final int MAX_ROWS = 60;

    private final MeasurementService measurementService;

    public MeasurementsTool(MeasurementService measurementService) {
        this.measurementService = measurementService;
    }

    @Override
    public String name() {
        return "get_measurements";
    }

    @Override
    public String description() {
        return "Lista las mediciones de pH más recientes del periodo, en orden cronológico (fecha UTC, pH, estado, origen). "
            + "Úsala para revisar valores concretos o un día específico.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of(
            "days", AgentTool.intParam("Días hacia atrás desde hoy", 1, 365),
            "limit", AgentTool.intParam("Máximo de mediciones a devolver (las más recientes)", 1, MAX_ROWS)), "required", List.of("days"));
    }

    @Override
    public Object execute(AgentContext context, JsonNode arguments) {
        int days = context.clampDays(AgentTool.intArg(arguments, "days", context.periodDays()));
        int limit = Math.max(1, Math.min(AgentTool.intArg(arguments, "limit", 30), MAX_ROWS));
        Instant now = Instant.now();
        List<Measurement> all = measurementService.between(context.userId(), now.minus(Duration.ofDays(days)), now);
        List<List<Object>> rows = all.subList(Math.max(0, all.size() - limit), all.size()).stream()
            .map(m -> java.util.Arrays.<Object>asList(AgentFormats.DATE.format(m.getMeasuredAt()), m.getPh(),
                m.getStatus().label(), m.getSource().name(), m.getTemperatureC()))
            .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dias", days);
        result.put("totalEnPeriodo", all.size());
        result.put("devueltas", rows.size());
        result.put("columnas", List.of("fecha", "ph", "estado", "origen", "temperaturaC"));
        result.put("mediciones", rows);
        return result;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Revisó las mediciones de " + AgentTool.intArg(arguments, "days", 7) + " días";
    }

    @Override
    public String summarize(Object result) {
        Map<?, ?> map = (Map<?, ?>) result;
        return map.get("devueltas") + " de " + map.get("totalEnPeriodo") + " mediciones";
    }
}
