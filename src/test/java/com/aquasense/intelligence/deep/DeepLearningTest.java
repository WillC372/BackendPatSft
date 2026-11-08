package com.aquasense.intelligence.deep;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.measurement.Measurement;
import com.aquasense.profile.PhRange;
import com.aquasense.profile.ProfileType;
import com.aquasense.profile.WaterProfile;
import com.aquasense.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DeepLearningTest {

    private static final WaterProfile POOL = new WaterProfile(null, "Piscina", ProfileType.POOL, new PhRange(7.0, 7.8, 6.5, 8.0), "", "");

    @Test
    void backpropagationMatchesNumericalGradient() {
        LstmNetwork net = new LstmNetwork(3, 4, 2, 42);
        Random r = new Random(7);
        double[][] x = new double[5][3];
        for (double[] row : x) {
            for (int k = 0; k < row.length; k++) {
                row[k] = r.nextGaussian();
            }
        }
        double[] y = {0.3, -0.5};
        double[] analytic = new double[net.parameterCount()];
        net.lossAndGradient(x, y, analytic);
        double[] p = net.parameters();
        double eps = 1e-6;
        double worst = 0;
        for (int i = 0; i < p.length; i++) {
            double original = p[i];
            p[i] = original + eps;
            double plus = net.lossAndGradient(x, y, null);
            p[i] = original - eps;
            double minus = net.lossAndGradient(x, y, null);
            p[i] = original;
            double numeric = (plus - minus) / (2 * eps);
            double rel = Math.abs(numeric - analytic[i]) / Math.max(1e-8, Math.abs(numeric) + Math.abs(analytic[i]));
            if (Math.abs(numeric - analytic[i]) > 1e-7) {
                worst = Math.max(worst, rel);
            }
        }
        assertThat(worst).as("error relativo máximo del gradiente").isLessThan(1e-4);
    }

    @Test
    void hourlySeriesInterpolatesShortGapsAndSplitsLongOnes() {
        Instant t0 = Instant.parse("2026-11-01T00:10:00Z");
        List<Measurement> data = List.of(
            TestData.measurement(1, 7.0, t0, POOL), TestData.measurement(2, 7.2, t0.plusSeconds(1200), POOL),
            TestData.measurement(3, 7.4, t0.plus(Duration.ofHours(3)), POOL),
            TestData.measurement(4, 7.0, t0.plus(Duration.ofHours(20)), POOL));
        List<List<HourlySeries.Point>> segments = HourlySeries.segments(data, 6);
        assertThat(segments).hasSize(2);
        List<HourlySeries.Point> first = segments.get(0);
        assertThat(first).hasSize(4);
        assertThat(first.get(0).ph()).isEqualTo(7.1, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(first.get(1).observed()).isFalse();
        assertThat(first.get(1).ph()).isEqualTo(7.2, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(first.get(3).observed()).isTrue();
    }

    @Test
    void refusesToTrainWithoutEnoughContinuousHistory() {
        List<Measurement> few = synthetic(3, 1);
        DeepForecastService.Model model = DeepForecastService.fit(few, 1, false, Instant.now());
        assertThat(model.trained()).isFalse();
        assertThat(model.toResponse(POOL).reason()).contains("ventanas");
    }

    @Test
    void networkLearnsDailyCycleWithPersistentDriftAndBeatsBaselines() {
        List<Measurement> data = synthetic(21, 5);
        Instant now = data.get(data.size() - 1).getMeasuredAt().plus(Duration.ofMinutes(30));
        DeepForecastService.Model model = DeepForecastService.fit(data, 9, false, now);
        DeepForecastResponse r = model.toResponse(POOL);
        assertThat(r.trained()).isTrue();
        assertThat(r.training().bestEpoch()).isPositive();
        assertThat(r.training().validationLoss().get(r.training().bestEpoch() - 1))
            .isLessThan(r.training().validationLoss().get(0));
        double lstm = r.evaluation().models().get(0).mae();
        double best = r.evaluation().models().stream().skip(1).mapToDouble(DeepForecastResponse.ModelScore::mae).min().orElseThrow();
        assertThat(lstm).as("MAE de la red frente a la mejor referencia " + best).isLessThan(best);
        assertThat(r.evaluation().networkChosen()).isTrue();
        assertThat(r.forecast().points()).hasSize(12);
        assertThat(r.forecast().points()).allSatisfy(p -> assertThat(p.lower()).isLessThanOrEqualTo(p.chosen()));
        assertThat(r.alert()).isNotNull();
        assertThat(r.explanation()).contains("menor");
    }

    /** pH with a daily cycle, persistent AR(1) drift and noise; one measurement per hour with occasional gaps. */
    static List<Measurement> synthetic(int days, long seed) {
        Random random = new Random(seed);
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(Duration.ofDays(days));
        List<Measurement> list = new ArrayList<>();
        double drift = 0;
        for (int h = 0; h < days * 24; h++) {
            drift = 0.92 * drift + random.nextGaussian() * 0.04;
            if (random.nextDouble() < 0.08) {
                continue;
            }
            double ph = 7.3 + 0.25 * Math.sin(2 * Math.PI * (h % 24) / 24.0) + drift + random.nextGaussian() * 0.01;
            list.add(TestData.measurement(h + 1, ph, start.plus(Duration.ofHours(h)).plusSeconds(600), POOL));
        }
        return list;
    }
}
