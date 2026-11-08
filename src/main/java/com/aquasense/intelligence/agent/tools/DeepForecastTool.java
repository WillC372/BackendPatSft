package com.aquasense.intelligence.agent.tools;

import com.aquasense.intelligence.agent.AgentContext;
import com.aquasense.intelligence.agent.AgentTool;
import com.aquasense.intelligence.deep.DeepForecastResponse;
import com.aquasense.intelligence.deep.DeepForecastService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DeepForecastTool implements AgentTool {

    private final DeepForecastService service;

    public DeepForecastTool(DeepForecastService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "get_neural_forecast";
    }

    @Override
    public String description() {
        return "pH forecast for the next 12 hours with an LSTM neural network trained on the user's history,"
            + " compared with simple baselines on unseen data, plus the early warning. If there is not enough data it says so.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Object execute(AgentContext context, JsonNode arguments) {
        DeepForecastResponse r = service.forecast(context.userId(), false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trained", r.trained());
        if (!r.trained()) {
            result.put("reason", r.reason());
            return result;
        }
        result.put("modelUsed", r.evaluation().chosenLabel());
        result.put("improvementVsBaselinePct", r.evaluation().skillPercent());
        result.put("meanPhErrors", r.evaluation().models().stream()
            .collect(java.util.stream.Collectors.toMap(DeepForecastResponse.ModelScore::label, DeepForecastResponse.ModelScore::mae,
                (a, b) -> a, LinkedHashMap::new)));
        if (r.forecast() != null) {
            result.put("lastPh", r.forecast().lastPh());
            result.put("forecast", r.forecast().points().stream().map(p -> Map.of("hours", p.hoursAhead(), "ph", p.chosen(),
                "min", p.lower(), "max", p.upper())).toList());
        }
        if (r.alert() != null) {
            result.put("alert", r.alert().message());
        }
        result.put("explanation", r.explanation());
        return result;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Consultó la predicción de la red neuronal";
    }

    @Override
    public String summarize(Object result) {
        Map<?, ?> map = (Map<?, ?>) result;
        return Boolean.TRUE.equals(map.get("trained")) ? "Modelo usado: " + map.get("modelUsed") : "Sin datos suficientes";
    }
}
