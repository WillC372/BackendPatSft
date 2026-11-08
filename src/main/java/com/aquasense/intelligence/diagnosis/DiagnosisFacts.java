package com.aquasense.intelligence.diagnosis;

import com.aquasense.intelligence.advice.Advice;
import com.aquasense.intelligence.analysis.Anomaly;
import com.aquasense.intelligence.analysis.Forecast;
import com.aquasense.intelligence.analysis.TrendResult;
import com.aquasense.knowledge.Citation;
import com.aquasense.measurement.Measurement;
import com.aquasense.profile.Classification;
import com.aquasense.profile.WaterProfile;
import java.util.List;

/**
 * Hechos calculados por el sistema (estadística y reglas) que alimentan el diagnóstico.
 * La IA razona solo sobre estos hechos; no recibe datos personales.
 */
public record DiagnosisFacts(WaterProfile profile, int periodDays, List<Measurement> measurements,
                             Classification latest, TrendResult trend, List<Anomaly> anomalies, Forecast forecast,
                             Advice baseAdvice, long simulatedCount, long realCount, long manualCount,
                             List<Citation> guidelines) {

    public DiagnosisFacts {
        guidelines = guidelines == null ? List.of() : List.copyOf(guidelines);
    }

    public DiagnosisFacts(WaterProfile profile, int periodDays, List<Measurement> measurements, Classification latest,
                          TrendResult trend, List<Anomaly> anomalies, Forecast forecast, Advice baseAdvice,
                          long simulatedCount, long realCount, long manualCount) {
        this(profile, periodDays, measurements, latest, trend, anomalies, forecast, baseAdvice, simulatedCount, realCount,
            manualCount, List.of());
    }

    public List<String> guidelineIds() {
        return guidelines.stream().map(Citation::id).toList();
    }

    public int count() {
        return measurements.size();
    }

    public boolean hasData() {
        return !measurements.isEmpty();
    }

    public boolean includesSimulated() {
        return simulatedCount > 0;
    }
}
