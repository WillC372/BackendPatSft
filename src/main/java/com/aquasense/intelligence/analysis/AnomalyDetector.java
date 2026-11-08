package com.aquasense.intelligence.analysis;

import com.aquasense.measurement.Measurement;
import com.aquasense.profile.WaterProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AnomalyDetector {

    private final List<AnomalyRule> rules;

    public AnomalyDetector(List<AnomalyRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<Anomaly> detect(Measurement current, List<Measurement> previous, WaterProfile profile) {
        return rules.stream().map(rule -> rule.evaluate(current, previous, profile))
            .flatMap(Optional::stream).toList();
    }

    public List<Anomaly> scan(List<Measurement> chronological, WaterProfile profile) {
        List<Anomaly> found = new ArrayList<>();
        for (int i = 0; i < chronological.size(); i++) {
            found.addAll(detect(chronological.get(i), chronological.subList(0, i), profile));
        }
        return found;
    }
}
