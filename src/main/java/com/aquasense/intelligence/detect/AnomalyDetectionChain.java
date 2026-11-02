package com.aquasense.intelligence.detect;

import com.aquasense.intelligence.engine.AnalysisInput;
import com.aquasense.intelligence.engine.AnomalyFinding;
import com.aquasense.intelligence.strategy.AnalysisStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnomalyDetectionChain {

    private final AnomalyDetector first;

    public AnomalyDetectionChain() {
        RangeBreachDetector range = new RangeBreachDetector();
        range.linkWith(new StatisticalOutlierDetector()).linkWith(new AbruptChangeDetector());
        this.first = range;
    }

    public List<AnomalyFinding> detect(AnalysisInput input, AnalysisStrategy strategy) {
        List<AnomalyFinding> findings = new ArrayList<>();
        first.run(input, strategy, findings);
        findings.sort(Comparator.comparing(AnomalyFinding::measuredAt).reversed());
        return findings;
    }
}
