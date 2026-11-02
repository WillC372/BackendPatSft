package com.aquasense.intelligence.detect;

import com.aquasense.intelligence.engine.AnalysisInput;
import com.aquasense.intelligence.engine.AnomalyFinding;
import com.aquasense.intelligence.strategy.AnalysisStrategy;
import java.util.List;

public abstract class AnomalyDetector {

    private AnomalyDetector next;

    public AnomalyDetector linkWith(AnomalyDetector nextDetector) {
        this.next = nextDetector;
        return nextDetector;
    }

    public final void run(AnalysisInput input, AnalysisStrategy strategy, List<AnomalyFinding> findings) {
        detect(input, strategy, findings);
        if (next != null) {
            next.run(input, strategy, findings);
        }
    }

    protected abstract void detect(AnalysisInput input, AnalysisStrategy strategy, List<AnomalyFinding> findings);
}
