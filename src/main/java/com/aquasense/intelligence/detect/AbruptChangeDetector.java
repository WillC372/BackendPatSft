package com.aquasense.intelligence.detect;

import com.aquasense.intelligence.engine.AnalysisInput;
import com.aquasense.intelligence.engine.AnomalyFinding;
import com.aquasense.intelligence.engine.FindingType;
import com.aquasense.intelligence.engine.Sample;
import com.aquasense.intelligence.engine.Severity;
import com.aquasense.intelligence.strategy.AnalysisStrategy;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public class AbruptChangeDetector extends AnomalyDetector {

    @Override
    protected void detect(AnalysisInput in, AnalysisStrategy strategy, List<AnomalyFinding> findings) {
        List<Sample> samples = in.samples();
        for (int i = 1; i < samples.size(); i++) {
            Sample previous = samples.get(i - 1);
            Sample current = samples.get(i);
            Duration gap = Duration.between(previous.at(), current.at());
            double delta = current.ph() - previous.ph();
            if (gap.compareTo(strategy.abruptChangeWindow()) <= 0 && Math.abs(delta) >= strategy.abruptChangePh()) {
                findings.add(new AnomalyFinding(FindingType.ABRUPT_CHANGE, Severity.WARNING, current.at(),
                        current.ph(),
                        String.format(Locale.US, "Cambio brusco de %+.2f unidades de pH en %d min", delta,
                                gap.toMinutes()),
                        String.format(Locale.US, "Para %s se considera brusco un cambio de %.2f o más en menos de %d "
                                + "horas. Puede deberse a un evento real o a un error del sensor. %s",
                                strategy.context(), strategy.abruptChangePh(),
                                strategy.abruptChangeWindow().toHours(), AnomalyFinding.NOT_A_DIAGNOSIS)));
            }
        }
    }
}
