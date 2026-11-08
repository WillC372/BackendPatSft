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
        return "Calcula estadísticas del pH del periodo (promedio, mínimo, máximo, desviación, pendiente por día, tendencia), "
            + "la comparación con el periodo anterior de igual duración, la distribución por estado y por origen.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of("days", AgentTool.intParam("Duración del periodo en días", 1, 365)),
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
        result.put("dias", days);
        result.put("mediciones", t.count());
        result.put("datosSuficientes", t.enoughData());
        result.put("mensaje", t.message());
        if (t.enoughData()) {
            result.put("promedio", t.mean());
            result.put("minimo", t.min());
            result.put("maximo", t.max());
            result.put("desviacion", t.stdDev());
            result.put("pendientePhPorDia", t.slopePerDay());
            result.put("tendencia", t.directionLabel());
            result.put("promedioPeriodoAnterior", t.previousMean());
            result.put("cambioVsPeriodoAnterior", t.changeVsPrevious());
        }
        result.put("medicionesPeriodoAnterior", previous.size());
        result.put("porEstado", t.statusDistribution());
        result.put("porOrigen", current.stream().collect(Collectors.groupingBy(m -> m.getSource().name(), Collectors.counting())));
        return result;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Calculó estadísticas de " + AgentTool.intArg(arguments, "days", 7) + " días";
    }

    @Override
    public String summarize(Object result) {
        Map<?, ?> map = (Map<?, ?>) result;
        return Boolean.TRUE.equals(map.get("datosSuficientes"))
            ? "promedio " + map.get("promedio") + ", tendencia " + String.valueOf(map.get("tendencia")).toLowerCase()
            : map.get("mediciones") + " mediciones (insuficientes para tendencia)";
    }
}
