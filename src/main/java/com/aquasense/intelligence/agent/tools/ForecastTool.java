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
        return "Predice el pH de los próximos días con regresión lineal e intervalo del 95 % usando los últimos 30 días. "
            + "Si no hay datos suficientes, devuelve el motivo y no predice.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of("horizon_days", AgentTool.intParam("Días a predecir", 1, 7)),
            "required", List.of("horizon_days"));
    }

    @Override
    public Object execute(AgentContext context, JsonNode arguments) {
        int horizon = Math.max(1, Math.min(AgentTool.intArg(arguments, "horizon_days", 3), context.maxHorizon()));
        Instant now = Instant.now();
        Forecast f = forecaster.forecast(measurementService.between(context.userId(), now.minus(Duration.ofDays(30)), now), horizon, now);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("disponible", f.available());
        if (!f.available()) {
            result.put("motivo", f.reason());
            return result;
        }
        result.put("horizonteDias", f.horizonDays());
        result.put("metodo", f.method());
        result.put("basadoEnMediciones", f.basedOn());
        result.put("r2", f.r2());
        result.put("pendientePhPorDia", f.slopePerDay());
        result.put("puntos", f.points().stream().map(p -> Map.of("fecha", AgentFormats.DATE.format(p.at()), "ph", p.predicted(),
            "minimo95", p.lower(), "maximo95", p.upper())).toList());
        result.put("limitaciones", f.limitations());
        return result;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Pidió una predicción a " + AgentTool.intArg(arguments, "horizon_days", 3) + " días";
    }

    @Override
    public String summarize(Object result) {
        Map<?, ?> map = (Map<?, ?>) result;
        if (!Boolean.TRUE.equals(map.get("disponible"))) {
            return "no disponible: " + map.get("motivo");
        }
        List<?> points = (List<?>) map.get("puntos");
        return "pH estimado " + ((Map<?, ?>) points.get(points.size() - 1)).get("ph") + " en " + map.get("horizonteDias") + " días";
    }
}
