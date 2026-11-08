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
        return "Predicción del pH para las próximas 12 horas con una red neuronal LSTM entrenada con el historial del usuario,"
            + " comparada con referencias simples en datos no vistos, más la alerta anticipada. Si no hay datos suficientes lo indica.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public Object execute(AgentContext context, JsonNode arguments) {
        DeepForecastResponse r = service.forecast(context.userId(), false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entrenado", r.trained());
        if (!r.trained()) {
            result.put("motivo", r.reason());
            return result;
        }
        result.put("modeloUsado", r.evaluation().chosenLabel());
        result.put("mejoraFrenteReferenciaPct", r.evaluation().skillPercent());
        result.put("erroresMediosPh", r.evaluation().models().stream()
            .collect(java.util.stream.Collectors.toMap(DeepForecastResponse.ModelScore::label, DeepForecastResponse.ModelScore::mae,
                (a, b) -> a, LinkedHashMap::new)));
        if (r.forecast() != null) {
            result.put("ultimoPh", r.forecast().lastPh());
            result.put("prediccion", r.forecast().points().stream().map(p -> Map.of("horas", p.hoursAhead(), "ph", p.chosen(),
                "minimo", p.lower(), "maximo", p.upper())).toList());
        }
        if (r.alert() != null) {
            result.put("alerta", r.alert().message());
        }
        result.put("explicacion", r.explanation());
        return result;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Consultó la predicción de la red neuronal";
    }

    @Override
    public String summarize(Object result) {
        Map<?, ?> map = (Map<?, ?>) result;
        return Boolean.TRUE.equals(map.get("entrenado")) ? "Modelo usado: " + map.get("modeloUsado") : "Sin datos suficientes";
    }
}
