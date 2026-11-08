package com.aquasense.intelligence.analysis;

import com.aquasense.measurement.Measurement;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TrendAnalyzer {

    static final int MIN_POINTS = 5;
    static final double STABLE_SLOPE = 0.05;
    private static final double SECONDS_PER_DAY = 86400.0;

    public TrendResult analyze(List<Measurement> current, List<Measurement> previousPeriod) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("OPTIMAL", 0L);
        distribution.put("CAUTION", 0L);
        distribution.put("CRITICAL", 0L);
        current.stream().collect(Collectors.groupingBy(m -> m.getStatus().name(), Collectors.counting()))
            .forEach(distribution::put);
        if (current.size() < MIN_POINTS) {
            return new TrendResult(false, "Se necesitan al menos " + MIN_POINTS
                + " mediciones en el periodo para analizar la tendencia (hay " + current.size() + ").",
                current.size(), Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, "UNKNOWN", "Sin datos suficientes",
                null, null, distribution);
        }
        double[] values = current.stream().mapToDouble(Measurement::getPh).toArray();
        Instant origin = current.get(0).getMeasuredAt();
        double[] days = current.stream()
            .mapToDouble(m -> Duration.between(origin, m.getMeasuredAt()).toSeconds() / SECONDS_PER_DAY).toArray();
        double slope = Statistics.linearRegression(days, values).slope();
        String direction = Math.abs(slope) < STABLE_SLOPE ? "STABLE" : slope > 0 ? "RISING" : "FALLING";
        String label = switch (direction) {
            case "RISING" -> "En aumento";
            case "FALLING" -> "En descenso";
            default -> "Estable";
        };
        Double previousMean = previousPeriod.isEmpty() ? null
            : Statistics.mean(previousPeriod.stream().mapToDouble(Measurement::getPh).toArray());
        double mean = Statistics.mean(values);
        Double change = previousMean == null ? null : mean - previousMean;
        String message = String.format(Locale.US, "Tendencia %s (%.3f pH/día) con %d mediciones. Promedio %.2f.",
            label.toLowerCase(Locale.ROOT), slope, values.length, mean)
            + (change == null ? " No hay datos del periodo anterior para comparar."
               : String.format(Locale.US, " Frente al periodo anterior: %+.2f.", change));
        double min = java.util.Arrays.stream(values).min().orElse(Double.NaN);
        double max = java.util.Arrays.stream(values).max().orElse(Double.NaN);
        return new TrendResult(true, message, values.length, round(mean), min, max, round(Statistics.stdDev(values)),
            round3(slope), direction, label, previousMean == null ? null : round(previousMean),
            change == null ? null : round(change), distribution);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

}
