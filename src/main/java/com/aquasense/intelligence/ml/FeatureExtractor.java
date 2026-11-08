package com.aquasense.intelligence.ml;

import com.aquasense.intelligence.analysis.Statistics;
import com.aquasense.measurement.Measurement;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a chronological series of measurements into feature vectors. Each vector only uses the measurement
 * and the previous ones (never future data), so the same computation serves for training and for scoring a new measurement.
 */
public final class FeatureExtractor {

    static final int CONTEXT = 10;
    static final double MAX_RATE = 10;
    private static final double MIN_HOURS = 1.0 / 60;

    private FeatureExtractor() {
    }

    public static boolean usesTemperature(List<Measurement> chronological) {
        return chronological.stream().filter(m -> m.getTemperatureC() != null).count() >= chronological.size() / 2.0;
    }

    public static List<Feature> features(boolean temperature) {
        List<Feature> features = new ArrayList<>(List.of(Feature.PH, Feature.DELTA, Feature.RATE, Feature.DEVIATION,
            Feature.HOUR_SIN, Feature.HOUR_COS));
        if (temperature) {
            features.add(Feature.TEMPERATURE);
        }
        return features;
    }

    public static double[] vector(List<Measurement> chronological, int index, boolean temperature, double temperatureFallback) {
        Measurement current = chronological.get(index);
        double ph = current.getPh();
        double delta = 0;
        double rate = 0;
        double deviation = 0;
        if (index > 0) {
            Measurement previous = chronological.get(index - 1);
            delta = ph - previous.getPh();
            double hours = Math.max(MIN_HOURS, Duration.between(previous.getMeasuredAt(), current.getMeasuredAt()).toSeconds() / 3600.0);
            rate = Math.max(-MAX_RATE, Math.min(MAX_RATE, delta / hours));
            double[] context = chronological.subList(Math.max(0, index - CONTEXT), index).stream()
                .mapToDouble(Measurement::getPh).toArray();
            deviation = ph - Statistics.median(context);
        }
        double hour = current.getMeasuredAt().atZone(ZoneOffset.UTC).getHour()
            + current.getMeasuredAt().atZone(ZoneOffset.UTC).getMinute() / 60.0;
        double angle = 2 * Math.PI * hour / 24;
        List<Double> values = new ArrayList<>(List.of(ph, delta, rate, deviation, Math.sin(angle), Math.cos(angle)));
        if (temperature) {
            values.add(current.getTemperatureC() == null ? temperatureFallback : current.getTemperatureC());
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
