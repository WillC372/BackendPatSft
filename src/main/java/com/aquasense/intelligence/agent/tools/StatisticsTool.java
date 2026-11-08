package com.aquasense.intelligence.agent.tools;

import com.aquasense.intelligence.agent.AgentContext;
import com.aquasense.intelligence.agent.AgentTool;
import com.aquasense.intelligence.analysis.TrendAnalyzer;
import com.aquasense.intelligence.analysis.TrendResult;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class StatisticsTool implements AgentTool {

    private final MeasurementService measurementService;
    private final TrendAnalyzer trendAnalyzer;

    public StatisticsTool(MeasurementService measurementService, TrendAnalyzer trendAnalyzer) {
        this.measurementService = measurementService;
        this.trendAnalyzer = trendAnalyzer;
    }

    @Override
    public String name() {
        return "get_statistics";
    }

    @Override
    public String description() {
        return "Computes pH statistics for the period (mean, min, max, deviation, slope per day, trend), "
            + "the comparison with the previous period of the same length and the distribution by status and by origin.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of("days", AgentTool.intParam("Length of the period in days", 1, 365)),
            "required", List.of("days"));
    }

    @Override
    public Object execute(AgentContext context, JsonNode arguments) {
        int days = context.clampDays(AgentTool.intArg(arguments, "days", context.periodDays()));
        Instant now = Instant.now();
        List<Measurement> current = measurementService.between(context.userId(), now.minus(Duration.ofDays(days)), now);
        List<Measurement> previous = measurementService.between(context.userId(), now.minus(Duration.ofDays(2L * days)),
            now.minus(Duration.ofDays(days)));
        TrendResult t = trendAnalyzer.analyze(current, previous);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("measurements", t.count());
        result.put("enoughData", t.enoughData());
        result.put("message", t.message());
        if (t.enoughData()) {
            result.put("mean", t.mean());
            result.put("min", t.min());
            result.put("max", t.max());
            result.put("stdDev", t.stdDev());
            result.put("phSlopePerDay", t.slopePerDay());
            result.put("trend", t.directionLabel());
            result.put("previousPeriodMean", t.previousMean());
            result.put("changeVsPreviousPeriod", t.changeVsPrevious());
        }
        result.put("previousPeriodMeasurements", previous.size());
        result.put("byStatus", t.statusDistribution());
        result.put("byOrigin", current.stream().collect(Collectors.groupingBy(m -> m.getSource().name(), Collectors.counting())));
        return result;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Calculó estadísticas de " + AgentTool.intArg(arguments, "days", 7) + " días";
    }

    @Override
    public String summarize(Object result) {
        Map<?, ?> map = (Map<?, ?>) result;
        return Boolean.TRUE.equals(map.get("enoughData"))
            ? "promedio " + map.get("mean") + ", tendencia " + String.valueOf(map.get("trend")).toLowerCase()
            : map.get("measurements") + " mediciones (insuficientes para tendencia)";
    }
}
