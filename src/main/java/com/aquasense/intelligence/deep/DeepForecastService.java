package com.aquasense.intelligence.deep;

import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementListener;
import com.aquasense.measurement.MeasurementRepository;
import com.aquasense.measurement.MeasurementSource;
import com.aquasense.profile.ProfileService;
import com.aquasense.profile.WaterProfile;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Layer 7: forecast with one LSTM network per user.
 *
 * <ol>
 *   <li>Turns the measurements of 90 days into an hourly series (gaps of up to 6 h are interpolated).</li>
 *   <li>Builds 24 h windows to predict the next 12 h and splits them in chronological order: 70 % training,
 *       15 % validation (early stopping) and 15 % test, with a gap between parts so no data leaks.</li>
 *   <li>Compares the network on the test set with three simple baselines: persistence, linear trend and
 *       daily seasonal. The network is only used if it reduces the error by at least 5 % against the best one; otherwise the
 *       baseline is used and the response says so.</li>
 *   <li>The interval of each hour is the 90th percentile of the absolute error on the test set, not an assumption.</li>
 * </ol>
 * The model is kept in memory and discarded when a new measurement arrives (Observer).
 */
@Service
public class DeepForecastService implements MeasurementListener {

    static final int TRAINING_DAYS = 90;
    static final int MAX_GAP_HOURS = 6;
    static final int HIDDEN = 16;
    static final int MIN_TRAIN = 100;
    static final int MIN_TEST = 20;
    static final int MAX_WINDOWS = 1500;
    static final double REQUIRED_SKILL = 0.05;
    static final long STALE_HOURS = 48;
    private static final String DISCLAIMER = "Predicción estadística a 12 horas basada en tu historial. No reemplaza una"
        + " medición; el pH por sí solo no demuestra potabilidad.";

    private final MeasurementRepository repository;
    private final ProfileService profileService;
    private final Map<Key, Model> cache = new ConcurrentHashMap<>();

    public DeepForecastService(MeasurementRepository repository, ProfileService profileService) {
        this.repository = repository;
        this.profileService = profileService;
    }

    @Override
    public void onMeasurementRecorded(Measurement measurement, WaterProfile profile) {
        cache.keySet().removeIf(key -> key.userId().equals(measurement.getUserId()));
    }

    public DeepForecastResponse forecast(Long userId, boolean includeSimulated) {
        Model model = cache.computeIfAbsent(new Key(userId, includeSimulated), key -> train(userId, includeSimulated));
        return model.toResponse(profileService.activeProfile(userId));
    }

    Model train(Long userId, boolean includeSimulated) {
        Instant now = Instant.now();
        List<Measurement> data = repository.findTop2000ByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(userId,
                now.minus(Duration.ofDays(TRAINING_DAYS)), now).stream()
            .filter(m -> includeSimulated || m.getSource() != MeasurementSource.SIMULATED).toList();
        return fit(data, userId, includeSimulated, now);
    }

    static Model fit(List<Measurement> data, long seed, boolean includeSimulated, Instant now) {
        boolean simulated = data.stream().anyMatch(m -> m.getSource() == MeasurementSource.SIMULATED);
        List<List<HourlySeries.Point>> segments = HourlySeries.segments(data, MAX_GAP_HOURS);
        List<HourlySeries.Point> all = segments.stream().flatMap(List::stream).toList();
        List<ForecastDataset.Window> windows = ForecastDataset.windows(segments);
        if (windows.size() > MAX_WINDOWS) {
            windows = windows.subList(windows.size() - MAX_WINDOWS, windows.size());
        }
        int gap = ForecastDataset.HORIZON;
        int required = MIN_TRAIN + 2 * MIN_TEST + 2 * gap;
        double span = data.size() < 2 ? 0
            : Duration.between(data.get(0).getMeasuredAt(), data.get(data.size() - 1).getMeasuredAt()).toHours() / 24.0;
        DeepForecastResponse.DataSummary summary = new DeepForecastResponse.DataSummary(data.size(), all.size(),
            (int) all.stream().filter(HourlySeries.Point::observed).count(),
            (int) all.stream().filter(p -> !p.observed()).count(), segments.size(), Math.round(span * 10) / 10.0,
            windows.size(), required);
        if (windows.size() < required) {
            return Model.notTrained(String.format(Locale.US, "La red necesita al menos %d ventanas de 36 horas continuas"
                + " (24 de entrada y 12 a predecir; los huecos de hasta %d h se interpolan). Con %d mediciones%s hay %d."
                + " En la práctica: unos 8 días con al menos una medición cada pocas horas.", required, MAX_GAP_HOURS,
                data.size(), includeSimulated ? "" : " reales o manuales", windows.size()), summary, simulated, all);
        }
        int n = windows.size();
        int testSize = Math.max(MIN_TEST, (int) Math.round(n * 0.15));
        int valSize = Math.max(MIN_TEST, (int) Math.round(n * 0.15));
        int trainEnd = n - testSize - valSize - 2 * gap;
        List<ForecastDataset.Window> train = windows.subList(0, trainEnd);
        List<ForecastDataset.Window> val = windows.subList(trainEnd + gap, trainEnd + gap + valSize);
        List<ForecastDataset.Window> test = windows.subList(n - testSize, n);
        ForecastDataset.Scaling scaling = ForecastDataset.Scaling.fit(train);

        long start = System.nanoTime();
        LstmNetwork network = new LstmNetwork(ForecastDataset.FEATURES.length, HIDDEN, ForecastDataset.HORIZON, seed * 31 + n);
        LstmNetwork.TrainingLog log = network.train(inputs(train, scaling), targets(train, scaling), inputs(val, scaling),
            targets(val, scaling), new LstmNetwork.Options(60, 32, 0.005, 5.0, 1e-5, 8, seed));
        long millis = (System.nanoTime() - start) / 1_000_000;

        Map<String, Function<ForecastDataset.Window, double[]>> predictors = new java.util.LinkedHashMap<>();
        predictors.put("lstm", w -> ForecastDataset.toPh(network.predict(ForecastDataset.features(w.input(), scaling)), w.last(), scaling));
        predictors.put("persistence", ForecastDataset::persistence);
        predictors.put("linear", ForecastDataset::linear);
        predictors.put("seasonal", ForecastDataset::seasonal);
        Map<String, double[][]> errors = new java.util.LinkedHashMap<>();
        predictors.forEach((id, p) -> errors.put(id, absoluteErrors(test, p)));
        List<DeepForecastResponse.ModelScore> scores = new ArrayList<>();
        errors.forEach((id, e) -> scores.add(new DeepForecastResponse.ModelScore(id, label(id), round(mean(e), 4))));
        DeepForecastResponse.ModelScore bestBaseline = scores.stream().filter(s -> !s.id().equals("lstm"))
            .min(Comparator.comparingDouble(DeepForecastResponse.ModelScore::mae)).orElseThrow();
        double lstmMae = scores.get(0).mae();
        double skill = bestBaseline.mae() == 0 ? 0 : (bestBaseline.mae() - lstmMae) / bestBaseline.mae();
        boolean networkChosen = skill >= REQUIRED_SKILL;
        String chosen = networkChosen ? "lstm" : bestBaseline.id();
        double[] band = quantileByHour(errors.get(chosen), 0.9);

        DeepForecastResponse.Evaluation evaluation = new DeepForecastResponse.Evaluation(scores, chosen, label(chosen),
            round(skill * 100, 1), networkChosen, byHour(errors.get("lstm")), byHour(errors.get(bestBaseline.id())),
            bestBaseline.label());
        DeepForecastResponse.Training training = new DeepForecastResponse.Training(log.epochs(), log.bestEpoch(),
            Arrays.stream(log.trainLoss()).map(v -> round(v, 5)).boxed().toList(),
            Arrays.stream(log.validationLoss()).map(v -> round(v, 5)).boxed().toList(), millis, train.size(), val.size(), test.size());
        DeepForecastResponse.Architecture architecture = new DeepForecastResponse.Architecture("LSTM (1 capa) + capa densa",
            ForecastDataset.FEATURES.length, HIDDEN, ForecastDataset.HORIZON, ForecastDataset.WINDOW, ForecastDataset.HORIZON,
            network.parameterCount(), List.of(ForecastDataset.FEATURES));

        List<HourlySeries.Point> lastSegment = segments.get(segments.size() - 1);
        DeepForecastResponse.Forecast forecast = null;
        if (lastSegment.size() >= ForecastDataset.WINDOW) {
            ForecastDataset.Window latest = new ForecastDataset.Window(
                List.copyOf(lastSegment.subList(lastSegment.size() - ForecastDataset.WINDOW, lastSegment.size())), null);
            double[] net = predictors.get("lstm").apply(latest);
            double[] base = predictors.get(bestBaseline.id()).apply(latest);
            double[] pick = predictors.get(chosen).apply(latest);
            Instant from = latest.input().get(ForecastDataset.WINDOW - 1).hour();
            List<DeepForecastResponse.ForecastPoint> points = new ArrayList<>();
            for (int k = 0; k < ForecastDataset.HORIZON; k++) {
                points.add(new DeepForecastResponse.ForecastPoint(from.plus(Duration.ofHours(k + 1L)), k + 1, round(net[k], 3),
                    round(base[k], 3), round(pick[k], 3), round(Math.max(0, pick[k] - band[k]), 3),
                    round(Math.min(14, pick[k] + band[k]), 3)));
            }
            forecast = new DeepForecastResponse.Forecast(from, round(latest.last(), 3),
                Duration.between(from, now).toHours() > STALE_HOURS, points,
                "Intervalo empírico del 90 %: percentil 90 del error en datos de prueba que el modelo no vio");
        }
        return new Model(true, null, summary, simulated, all, architecture, training, evaluation, forecast);
    }

    private static double[][][] inputs(List<ForecastDataset.Window> windows, ForecastDataset.Scaling scaling) {
        return windows.stream().map(w -> ForecastDataset.features(w.input(), scaling)).toArray(double[][][]::new);
    }

    private static double[][] targets(List<ForecastDataset.Window> windows, ForecastDataset.Scaling scaling) {
        return windows.stream().map(w -> ForecastDataset.target(w, scaling)).toArray(double[][]::new);
    }

    private static double[][] absoluteErrors(List<ForecastDataset.Window> test, Function<ForecastDataset.Window, double[]> predictor) {
        double[][] e = new double[test.size()][];
        for (int i = 0; i < test.size(); i++) {
            double[] p = predictor.apply(test.get(i));
            e[i] = new double[p.length];
            for (int k = 0; k < p.length; k++) {
                e[i][k] = Math.abs(p[k] - test.get(i).future()[k]);
            }
        }
        return e;
    }

    private static double mean(double[][] e) {
        return Arrays.stream(e).flatMapToDouble(Arrays::stream).average().orElse(0);
    }

    private static List<Double> byHour(double[][] e) {
        List<Double> list = new ArrayList<>();
        for (int k = 0; k < ForecastDataset.HORIZON; k++) {
            final int h = k;
            list.add(round(Arrays.stream(e).mapToDouble(r -> r[h]).average().orElse(0), 4));
        }
        return list;
    }

    private static double[] quantileByHour(double[][] e, double q) {
        double[] band = new double[ForecastDataset.HORIZON];
        for (int k = 0; k < band.length; k++) {
            final int h = k;
            double[] sorted = Arrays.stream(e).mapToDouble(r -> r[h]).sorted().toArray();
            band[k] = sorted[Math.min(sorted.length - 1, (int) Math.ceil(q * sorted.length) - 1)];
        }
        return band;
    }

    static String label(String id) {
        return switch (id) {
            case "lstm" -> "Red neuronal LSTM";
            case "persistence" -> "Persistencia (sin cambio)";
            case "linear" -> "Tendencia lineal";
            case "seasonal" -> "Estacional diaria (misma hora de ayer)";
            default -> id;
        };
    }

    static double round(double v, int decimals) {
        double s = Math.pow(10, decimals);
        return Math.round(v * s) / s;
    }

    record Key(Long userId, boolean includeSimulated) {
    }

    record Model(boolean trained, String reason, DeepForecastResponse.DataSummary data, boolean simulated,
                 List<HourlySeries.Point> series, DeepForecastResponse.Architecture architecture,
                 DeepForecastResponse.Training training, DeepForecastResponse.Evaluation evaluation,
                 DeepForecastResponse.Forecast forecast) {

        static Model notTrained(String reason, DeepForecastResponse.DataSummary data, boolean simulated,
                                List<HourlySeries.Point> series) {
            return new Model(false, reason, data, simulated, series, null, null, null, null);
        }

        DeepForecastResponse toResponse(WaterProfile profile) {
            List<DeepForecastResponse.HistoryPoint> history = series.subList(Math.max(0, series.size() - 48), series.size())
                .stream().map(p -> new DeepForecastResponse.HistoryPoint(p.hour(), round(p.ph(), 3), p.observed())).toList();
            if (!trained) {
                return new DeepForecastResponse(false, reason, simulated, data, null, null, null, null, null, history,
                    null, DISCLAIMER);
            }
            DeepForecastResponse.Alert alert = forecast == null ? null : alert(forecast, profile);
            return new DeepForecastResponse(true, null, simulated, data, architecture, training, evaluation, forecast, alert,
                history, explanation(), DISCLAIMER);
        }

        private String explanation() {
            String text = evaluation.networkChosen()
                ? String.format(Locale.US, "En %d ventanas de prueba que la red no vio, su error medio fue %.3f pH: %.1f %% menor"
                    + " que la mejor referencia simple (%s). Por eso la predicción usa la red.", training.testWindows(),
                    evaluation.models().get(0).mae(), evaluation.skillPercent(), evaluation.bestBaselineLabel().toLowerCase(Locale.ROOT))
                : String.format(Locale.US, "En %d ventanas de prueba la red tuvo un error medio de %.3f pH y no superó en al menos"
                    + " 5 %% a la referencia \"%s\" (diferencia %.1f %%). Por eso la predicción usa esa referencia: un modelo"
                    + " complejo solo se usa si demuestra ser mejor.", training.testWindows(), evaluation.models().get(0).mae(),
                    evaluation.bestBaselineLabel().toLowerCase(Locale.ROOT), evaluation.skillPercent());
            return simulated ? text + " Atención: el entrenamiento incluye datos del simulador, que no representan agua real." : text;
        }

        private static DeepForecastResponse.Alert alert(DeepForecastResponse.Forecast f, WaterProfile p) {
            if (f.stale()) {
                return new DeepForecastResponse.Alert("INFO", "Las últimas mediciones tienen más de 48 horas: la predicción parte"
                    + " de ese momento y no describe el agua de hoy.", null, null);
            }
            for (DeepForecastResponse.ForecastPoint point : f.points()) {
                double v = point.chosen();
                if (v < p.getCautionMin() || v > p.getCautionMax()) {
                    return new DeepForecastResponse.Alert("CRITICAL", String.format(Locale.US,
                        "Se prevé que el pH llegue a %.2f en unas %d h, fuera del rango de precaución (%.1f - %.1f).",
                        v, point.hoursAhead(), p.getCautionMin(), p.getCautionMax()), point.hoursAhead(), v);
                }
            }
            boolean inside = f.lastPh() >= p.getIdealMin() && f.lastPh() <= p.getIdealMax();
            for (DeepForecastResponse.ForecastPoint point : f.points()) {
                double v = point.chosen();
                if (inside && (v < p.getIdealMin() || v > p.getIdealMax())) {
                    return new DeepForecastResponse.Alert("CAUTION", String.format(Locale.US,
                        "Alerta anticipada: se prevé que el pH salga del rango ideal (%.1f - %.1f) en unas %d h (pH %.2f).",
                        p.getIdealMin(), p.getIdealMax(), point.hoursAhead(), v), point.hoursAhead(), v);
                }
            }
            return new DeepForecastResponse.Alert("OK", "No se prevé que el pH salga del rango ideal en las próximas 12 horas.",
                null, null);
        }
    }
}
