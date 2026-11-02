package com.aquasense.intelligence.detect;

import com.aquasense.intelligence.engine.AnalysisInput;
import com.aquasense.intelligence.engine.AnomalyFinding;
import com.aquasense.intelligence.engine.FindingType;
import com.aquasense.intelligence.engine.Sample;
import com.aquasense.intelligence.engine.Severity;
import com.aquasense.intelligence.strategy.AnalysisStrategy;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class StatisticalOutlierDetector extends AnomalyDetector {

    static final int RECENT_COUNT = 3;
    static final int MIN_BASELINE = 7;
    private static final double MAD_TO_SIGMA = 1.4826;
    private static final double MIN_SCALE = 0.05;

    @Override
    protected void detect(AnalysisInput in, AnalysisStrategy strategy, List<AnomalyFinding> findings) {
        List<Sample> samples = in.samples();
        int baselineSize = samples.size() - RECENT_COUNT;
        if (baselineSize < MIN_BASELINE) {
            return;
        }
        double[] baseline = samples.subList(0, baselineSize).stream().mapToDouble(Sample::ph).toArray();
        double median = median(baseline);
        double mad = median(Arrays.stream(baseline).map(v -> Math.abs(v - median)).toArray());
        double scale = Math.max(mad * MAD_TO_SIGMA, MIN_SCALE);
        for (Sample s : samples.subList(baselineSize, samples.size())) {
            double z = (s.ph() - median) / scale;
            if (Math.abs(z) >= strategy.outlierRobustZ()) {
                findings.add(new AnomalyFinding(FindingType.STATISTICAL_OUTLIER,
                        Math.abs(z) >= strategy.outlierRobustZ() * 2 ? Severity.CRITICAL : Severity.WARNING,
                        s.at(), s.ph(),
                        String.format(Locale.US, "pH %.2f se aparta de lo habitual (%.2f)", s.ph(), median),
                        String.format(Locale.US, "Su historial reciente ronda %.2f y esta lectura difiere %.1f "
                                + "desviaciones robustas (umbral %.1f para %s). %s", median, Math.abs(z),
                                strategy.outlierRobustZ(), strategy.context(), AnomalyFinding.NOT_A_DIAGNOSIS)));
            }
        }
    }

    static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }
}
