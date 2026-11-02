package com.aquasense.intelligence.detect;

import com.aquasense.intelligence.engine.AnalysisInput;
import com.aquasense.intelligence.engine.AnomalyFinding;
import com.aquasense.intelligence.engine.FindingType;
import com.aquasense.intelligence.engine.Sample;
import com.aquasense.intelligence.engine.Severity;
import com.aquasense.intelligence.strategy.AnalysisStrategy;
import java.util.List;
import java.util.Locale;

public class RangeBreachDetector extends AnomalyDetector {

    @Override
    protected void detect(AnalysisInput in, AnalysisStrategy strategy, List<AnomalyFinding> findings) {
        for (Sample s : in.samples()) {
            if (s.ph() >= in.cautionMin() && s.ph() <= in.cautionMax()) {
                continue;
            }
            findings.add(new AnomalyFinding(FindingType.OUT_OF_RANGE, Severity.CRITICAL, s.at(), s.ph(),
                    String.format(Locale.US, "pH %.2f fuera de la zona de precaución del perfil «%s»", s.ph(),
                            in.profileName()),
                    String.format(Locale.US, "El perfil define precaución entre %.2f y %.2f. %s", in.cautionMin(),
                            in.cautionMax(), AnomalyFinding.NOT_A_DIAGNOSIS)));
        }
    }
}
