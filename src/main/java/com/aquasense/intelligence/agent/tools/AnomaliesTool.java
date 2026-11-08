package com.aquasense.intelligence.agent.tools;

import com.aquasense.intelligence.agent.AgentContext;
import com.aquasense.intelligence.agent.AgentTool;
import com.aquasense.intelligence.analysis.Anomaly;
import com.aquasense.intelligence.analysis.AnomalyDetector;
import com.aquasense.measurement.MeasurementService;
import com.aquasense.profile.ProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AnomaliesTool implements AgentTool {

    private final MeasurementService measurementService;
    private final ProfileService profileService;
    private final AnomalyDetector detector;

    public AnomaliesTool(MeasurementService measurementService, ProfileService profileService, AnomalyDetector detector) {
        this.measurementService = measurementService;
        this.profileService = profileService;
        this.detector = detector;
    }

    @Override
    public String name() {
        return "get_anomalies";
    }

    @Override
    public String description() {
        return "Detects anomalies in the period with the system rules: outside the profile range, sudden change "
            + "(0.5 pH or more in one hour), outlier (robust z-score above 3.5) and unusual behavior "
            + "according to the user's machine learning model (type ML_BEHAVIOR, with an explanation of the variables).";
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
        var measurements = measurementService.between(context.userId(), now.minus(Duration.ofDays(days)), now);
        List<Anomaly> anomalies = detector.scan(measurements, profileService.activeProfile(context.userId())).stream()
            .sorted(Comparator.comparing(Anomaly::measuredAt).reversed()).limit(15).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("analyzedMeasurements", measurements.size());
        result.put("total", anomalies.size());
        result.put("anomalies", anomalies.stream().map(a -> Map.of("date", AgentFormats.DATE.format(a.measuredAt()),
            "ph", a.ph(), "type", a.title(), "severity", a.severity().name(), "detail", a.message())).toList());
        return result;
    }

    @Override
    public String label(JsonNode arguments) {
        return "Buscó anomalías en " + AgentTool.intArg(arguments, "days", 7) + " días";
    }

    @Override
    public String summarize(Object result) {
        return ((Map<?, ?>) result).get("total") + " anomalías encontradas";
    }
}
