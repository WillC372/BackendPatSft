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
        return "Lists the most recent pH measurements of the period, in chronological order (UTC date, pH, status, origin). "
            + "Use it to check specific values or a specific day.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of(
            "days", AgentTool.intParam("Days back from today", 1, 365),
            "limit", AgentTool.intParam("Maximum number of measurements to return (the most recent ones)", 1, MAX_ROWS)), "required", List.of("days"));
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
        result.put("days", days);
        result.put("totalInPeriod", all.size());
        result.put("returned", rows.size());
        result.put("columns", List.of("date", "ph", "status", "origin", "temperatureC"));
        result.put("measurements", rows);
        return result;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Revisó las mediciones de " + AgentTool.intArg(arguments, "days", 7) + " días";
    }

    @Override
    public String summarize(Object result) {
        Map<?, ?> map = (Map<?, ?>) result;
        return map.get("returned") + " de " + map.get("totalInPeriod") + " mediciones";
    }
}
