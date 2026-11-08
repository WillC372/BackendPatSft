package com.aquasense.intelligence.deep;

import com.aquasense.measurement.Measurement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Convierte mediciones irregulares en una serie horaria: promedia las mediciones de cada hora e interpola
 * linealmente los huecos cortos. Un hueco mayor que {@code maxGapHours} corta la serie en segmentos, porque
 * inventar varias horas seguidas de datos enseñaría a la red patrones que no existen.
 */
public final class HourlySeries {

    private HourlySeries() {
    }

    public record Point(Instant hour, double ph, boolean observed) {
    }

    public static List<List<Point>> segments(List<Measurement> measurements, int maxGapHours) {
        TreeMap<Instant, double[]> buckets = new TreeMap<>();
        for (Measurement m : measurements) {
            double[] acc = buckets.computeIfAbsent(m.getMeasuredAt().truncatedTo(ChronoUnit.HOURS), k -> new double[2]);
            acc[0] += m.getPh();
            acc[1]++;
        }
        List<List<Point>> segments = new ArrayList<>();
        List<Point> current = new ArrayList<>();
        Map.Entry<Instant, double[]> previous = null;
        for (Map.Entry<Instant, double[]> entry : buckets.entrySet()) {
            double ph = entry.getValue()[0] / entry.getValue()[1];
            if (previous != null) {
                long gap = Duration.between(previous.getKey(), entry.getKey()).toHours() - 1;
                double prevPh = previous.getValue()[0] / previous.getValue()[1];
                if (gap > maxGapHours) {
                    segments.add(current);
                    current = new ArrayList<>();
                } else {
                    for (int k = 1; k <= gap; k++) {
                        double fraction = (double) k / (gap + 1);
                        current.add(new Point(previous.getKey().plus(Duration.ofHours(k)), prevPh + fraction * (ph - prevPh), false));
                    }
                }
            }
            current.add(new Point(entry.getKey(), ph, true));
            previous = entry;
        }
        if (!current.isEmpty()) {
            segments.add(current);
        }
        return segments;
    }
}
