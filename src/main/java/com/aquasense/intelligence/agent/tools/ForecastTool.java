package com.aquasense.intelligence.agent.tools;

import com.aquasense.intelligence.agent.AgentContext;
import com.aquasense.intelligence.agent.AgentTool;
import com.aquasense.intelligence.analysis.Forecast;
import com.aquasense.intelligence.analysis.Forecaster;
import com.aquasense.measurement.MeasurementService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ForecastTool implements AgentTool {

    private final MeasurementService measurementService;
    private final Forecaster forecaster;

    public ForecastTool(MeasurementService measurementService, Forecaster forecaster) {
        this.measurementService = measurementService;
        this.forecaster = forecaster;
    }

    @Override
    public String name() {
        return "get_forecast";
    }

    @Override
    public String description() {
        return "Forecasts the pH of the coming days with linear regression and a 95 % interval using the last 30 days. "
            + "If there is not enough data, it returns the reason and does not forecast.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of("horizon_days", AgentTool.intParam("Days to forecast", 1, 7)),
            "required", List.of("horizon_days"));
    }

    @Override
    public Object execute(AgentContext context, JsonNode arguments) {
        int horizon = Math.max(1, Math.min(AgentTool.intArg(arguments, "horizon_days", 3), context.maxHorizon()));
        Instant now = Instant.now();
        Forecast f = forecaster.forecast(measurementService.between(context.userId(), now.minus(Duration.ofDays(30)), now), horizon, now);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", f.available());
        if (!f.available()) {
            result.put("reason", f.reason());
            return result;
        }
        result.put("horizonDays", f.horizonDays());
        result.put("method", f.method());
        result.put("basedOnMeasurements", f.basedOn());
        result.put("r2", f.r2());
        result.put("phSlopePerDay", f.slopePerDay());
        result.put("points", f.points().stream().map(p -> Map.of("date", AgentFormats.DATE.format(p.at()), "ph", p.predicted(),
            "min95", p.lower(), "max95", p.upper())).toList());
        result.put("limitations", f.limitations());
        return result;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Pidió una predicción a " + AgentTool.intArg(arguments, "horizon_days", 3) + " días";
    }

    @Override
    public String summarize(Object result) {
        Map<?, ?> map = (Map<?, ?>) result;
        if (!Boolean.TRUE.equals(map.get("available"))) {
            return "no disponible: " + map.get("reason");
        }
        List<?> points = (List<?>) map.get("points");
        return "pH estimado " + ((Map<?, ?>) points.get(points.size() - 1)).get("ph") + " en " + map.get("horizonDays") + " días";
    }
}
