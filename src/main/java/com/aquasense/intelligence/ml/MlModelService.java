package com.aquasense.intelligence.ml;

import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementListener;
import com.aquasense.measurement.MeasurementRepository;
import com.aquasense.measurement.MeasurementSource;
import com.aquasense.profile.WaterProfile;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Trains and keeps in memory one Isolation Forest model per user with their last 90 days of measurements.
 * It observes measurements: when a new one arrives, it discards that user's model and retrains it
 * on the next request (training hundreds of points takes milliseconds).
 */
@Service
public class MlModelService implements MeasurementListener {

    public static final int MIN_SAMPLES = 30;
    public static final double MIN_SPAN_DAYS = 3;
    static final int TRAINING_DAYS = 90;
    static final int TREES = 100;
    static final int MAX_SAMPLE = 256;
    static final double CONTAMINATION = 0.03;
    static final double MIN_THRESHOLD = 0.6;

    private final MeasurementRepository repository;
    /** A trained model is kept while it is used: 30 minutes without use, or more than 200 models, and it is dropped. */
    static final java.time.Duration MODEL_IDLE = java.time.Duration.ofMinutes(30);
    static final int MAX_MODELS = 200;
    private final com.aquasense.common.ExpiringCache<CacheKey, Training> cache =
        new com.aquasense.common.ExpiringCache<>(MODEL_IDLE, MAX_MODELS);

    public MlModelService(MeasurementRepository repository) {
        this.repository = repository;
    }

    public Training model(Long userId, boolean includeSimulated) {
        return cache.get(new CacheKey(userId, includeSimulated), key -> train(userId, includeSimulated));
    }

    @Override
    public void onMeasurementRecorded(Measurement measurement, WaterProfile profile) {
        cache.removeIf(key -> key.userId().equals(measurement.getUserId()));
    }

    public void invalidate(Long userId) {
        cache.removeIf(key -> key.userId().equals(userId));
    }

    /** Every 10 minutes the models nobody used in the last 30 are freed. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void forgetUnusedModels() {
        cache.evictExpired();
    }

    int cachedModels() {
        return cache.size();
    }

    /**
     * Scores a measurement. Isolation Forest does not extrapolate: a point outside the range seen in training gets
     * the same score as the most extreme training point. That is why, if the measurement is not part of the model yet,
     * the model is retrained including it (milliseconds) and the point is scored in-sample, as the algorithm assumes.
     */
    public Optional<Evaluation> evaluate(Measurement candidate) {
        Training training = model(candidate.getUserId(), false);
        if (!training.trained()) {
            return Optional.empty();
        }
        List<Measurement> data = training.data();
        int index = indexOf(data, candidate);
        UserAnomalyModel model = training.model();
        if (index < 0) {
            List<Measurement> extended = new java.util.ArrayList<>(data);
            extended.add(candidate);
            extended.sort(java.util.Comparator.comparing(Measurement::getMeasuredAt));
            Training adhoc = fit(candidate.getUserId(), extended);
            if (!adhoc.trained()) {
                return Optional.empty();
            }
            model = adhoc.model();
            data = extended;
            index = indexOf(data, candidate);
        }
        double[] vector = model.vector(data, index);
        return Optional.of(new Evaluation(model, vector, model.score(vector)));
    }

    private static int indexOf(List<Measurement> data, Measurement candidate) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i) == candidate || (candidate.getId() != null && candidate.getId().equals(data.get(i).getId()))) {
                return i;
            }
        }
        return -1;
    }

    public record Evaluation(UserAnomalyModel model, double[] vector, double score) {
        public boolean anomalous() {
            return model.isAnomalous(score);
        }
    }

    Training train(Long userId, boolean includeSimulated) {
        Instant now = Instant.now();
        List<Measurement> data = repository.findTop2000ByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(userId,
                now.minus(Duration.ofDays(TRAINING_DAYS)), now).stream()
            .filter(m -> includeSimulated || m.getSource() != MeasurementSource.SIMULATED)
            .toList();
        return fit(userId, data);
    }

    private Training fit(Long userId, List<Measurement> data) {
        boolean includeSimulated = data.stream().anyMatch(m -> m.getSource() == MeasurementSource.SIMULATED);
        if (data.size() < MIN_SAMPLES) {
            return Training.notTrained(data, "Se necesitan al menos " + MIN_SAMPLES + " mediciones"
                + (includeSimulated ? "" : " reales o manuales") + " en los últimos " + TRAINING_DAYS + " días; hay " + data.size() + ".");
        }
        double span = Duration.between(data.get(0).getMeasuredAt(), data.get(data.size() - 1).getMeasuredAt()).toSeconds() / 86400.0;
        if (span < MIN_SPAN_DAYS) {
            return Training.notTrained(data, String.format(Locale.US,
                "Las mediciones cubren %.1f días; se necesitan al menos %.0f para aprender el comportamiento habitual.", span, MIN_SPAN_DAYS));
        }
        long start = System.nanoTime();
        boolean temperature = FeatureExtractor.usesTemperature(data);
        double fallback = temperature ? data.stream().filter(m -> m.getTemperatureC() != null)
            .mapToDouble(Measurement::getTemperatureC).sorted().skip(data.size() / 4).findFirst().orElse(0) : 0;
        double[][] raw = new double[data.size()][];
        for (int i = 0; i < data.size(); i++) {
            raw[i] = FeatureExtractor.vector(data, i, temperature, fallback);
        }
        RobustScaler scaler = RobustScaler.fit(raw);
        double[][] scaled = Arrays.stream(raw).map(scaler::transform).toArray(double[][]::new);
        IsolationForest forest = IsolationForest.train(scaled, TREES, MAX_SAMPLE, userId * 7919L + data.size());
        double[] scores = Arrays.stream(scaled).mapToDouble(forest::score).sorted().toArray();
        double threshold = Math.max(MIN_THRESHOLD, RobustScaler.quantile(scores, 1 - CONTAMINATION));
        long millis = (System.nanoTime() - start) / 1_000_000;
        UserAnomalyModel model = new UserAnomalyModel(forest, scaler, FeatureExtractor.features(temperature), temperature,
            fallback, threshold, data.size(), millis);
        return new Training(model, null, data, span);
    }

    public record Training(UserAnomalyModel model, String reason, List<Measurement> data, double spanDays) {

        static Training notTrained(List<Measurement> data, String reason) {
            return new Training(null, reason, data, 0);
        }

        public boolean trained() {
            return model != null;
        }
    }

    private record CacheKey(Long userId, boolean includeSimulated) {
    }
}
