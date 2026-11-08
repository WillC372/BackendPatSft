package com.aquasense.intelligence.analysis;

import com.aquasense.measurement.Measurement;
import com.aquasense.profile.WaterProfile;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Puntaje z robusto (Iglewicz y Hoaglin): z = 0.6745 * (x - mediana) / MAD.
 * Con |z| > 3.5 el valor es atípico respecto al historial reciente del usuario.
 */
@Component
@Order(3)
public class StatisticalOutlierRule implements AnomalyRule {

    static final int MIN_HISTORY = 10;
    static final int WINDOW = 50;
    private static final double THRESHOLD = 3.5;
    private static final double MIN_MAD = 0.02;

    @Override
    public Optional<Anomaly> evaluate(Measurement current, List<Measurement> previous, WaterProfile profile) {
        if (previous.size() < MIN_HISTORY) {
            return Optional.empty();
        }
        double[] values = previous.subList(Math.max(0, previous.size() - WINDOW), previous.size()).stream()
            .mapToDouble(Measurement::getPh).toArray();
        double median = Statistics.median(values);
        double mad = Math.max(Statistics.medianAbsoluteDeviation(values), MIN_MAD);
        double score = 0.6745 * (current.getPh() - median) / mad;
        if (Math.abs(score) <= THRESHOLD) {
            return Optional.empty();
        }
        String message = String.format(Locale.US,
            "pH %.2f es inusual frente a tus últimas %d mediciones (mediana %.2f, puntaje z robusto %.1f).",
            current.getPh(), values.length, median, score);
        return Optional.of(new Anomaly("STATISTICAL_OUTLIER", Anomaly.Severity.CAUTION, "Valor atípico",
            message, current.getId(), current.getMeasuredAt(), current.getPh()));
    }
}
