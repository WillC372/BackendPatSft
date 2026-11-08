package com.aquasense.intelligence.ml;

import com.aquasense.measurement.Measurement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Modelo entrenado para un usuario: escalador, bosque de aislamiento, umbral y forma de explicar sus resultados.
 */
public final class UserAnomalyModel {

    private final IsolationForest forest;
    private final RobustScaler scaler;
    private final List<Feature> features;
    private final boolean temperature;
    private final double temperatureFallback;
    private final double threshold;
    private final int trainingSize;
    private final Instant trainedAt;
    private final long trainingMillis;

    UserAnomalyModel(IsolationForest forest, RobustScaler scaler, List<Feature> features, boolean temperature,
                     double temperatureFallback, double threshold, int trainingSize, long trainingMillis) {
        this.forest = forest;
        this.scaler = scaler;
        this.features = List.copyOf(features);
        this.temperature = temperature;
        this.temperatureFallback = temperatureFallback;
        this.threshold = threshold;
        this.trainingSize = trainingSize;
        this.trainedAt = Instant.now();
        this.trainingMillis = trainingMillis;
    }

    public double[] vector(List<Measurement> chronological, int index) {
        return FeatureExtractor.vector(chronological, index, temperature, temperatureFallback);
    }

    public double score(double[] vector) {
        return forest.score(scaler.transform(vector));
    }

    public boolean isAnomalous(double score) {
        return score >= threshold;
    }

    /** Las variables que más se alejan de lo típico para este usuario, explicadas en español. */
    public List<Factor> explain(double[] vector, Measurement measurement) {
        double[] scaled = scaler.transform(vector);
        List<Factor> factors = new ArrayList<>();
        boolean hourAdded = false;
        for (int i = 0; i < features.size(); i++) {
            Feature feature = features.get(i);
            if (feature == Feature.HOUR_SIN || feature == Feature.HOUR_COS) {
                if (hourAdded) {
                    continue;
                }
                int sin = features.indexOf(Feature.HOUR_SIN);
                int cos = features.indexOf(Feature.HOUR_COS);
                double weight = Math.hypot(scaled[sin], scaled[cos]);
                var time = measurement.getMeasuredAt().atZone(ZoneOffset.UTC);
                factors.add(new Factor(feature.label(), String.format(Locale.US, "%02d:%02d UTC", time.getHour(), time.getMinute()),
                    "poco habitual en tu historial", weight));
                hourAdded = true;
                continue;
            }
            String unit = feature.unit().isEmpty() ? "" : " " + feature.unit();
            String value = String.format(Locale.US, feature == Feature.PH ? "%.2f%s" : "%+.2f%s", vector[i], unit);
            String typical = String.format(Locale.US, "lo típico es %s%s", String.format(Locale.US,
                feature == Feature.PH || feature == Feature.TEMPERATURE ? "%.2f" : "%+.2f", scaler.center(i)), unit);
            factors.add(new Factor(feature.label(), value, typical, Math.abs(scaled[i])));
        }
        factors.sort(Comparator.comparingDouble(Factor::weight).reversed());
        return factors.subList(0, Math.min(3, factors.size()));
    }

    public List<String> featureLabels() {
        return features.stream().map(Feature::label).distinct().toList();
    }

    public double threshold() { return threshold; }
    public int trainingSize() { return trainingSize; }
    public int treeCount() { return forest.treeCount(); }
    public int sampleSize() { return forest.sampleSize(); }
    public Instant trainedAt() { return trainedAt; }
    public long trainingMillis() { return trainingMillis; }

    public record Factor(String label, String value, String typical, double weight) {
    }
}
