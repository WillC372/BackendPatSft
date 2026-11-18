package com.aquaassistant.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquaassistant.intelligence.analysis.Anomaly;
import com.aquaassistant.intelligence.analysis.AnomalyDetector;
import com.aquaassistant.intelligence.analysis.Forecast;
import com.aquaassistant.intelligence.analysis.Forecaster;
import com.aquaassistant.intelligence.analysis.OutOfRangeRule;
import com.aquaassistant.intelligence.analysis.StatisticalOutlierRule;
import com.aquaassistant.intelligence.analysis.Statistics;
import com.aquaassistant.intelligence.analysis.SuddenChangeRule;
import com.aquaassistant.intelligence.analysis.TrendAnalyzer;
import com.aquaassistant.intelligence.analysis.TrendResult;
import com.aquaassistant.measurement.Measurement;
import com.aquaassistant.profile.WaterProfile;
import com.aquaassistant.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisTest {

    private final WaterProfile pool = TestData.pool();
    private final AnomalyDetector detector = new AnomalyDetector(
        List.of(new OutOfRangeRule(), new SuddenChangeRule(), new StatisticalOutlierRule()));

    @Test
    void regressionRecoversKnownLine() {
        Statistics.Regression r = Statistics.linearRegression(new double[] {0, 1, 2, 3}, new double[] {7, 7.1, 7.2, 7.3});
        assertThat(r.slope()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(r.r2()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void detectsOutOfRangeSuddenChangeAndOutlier() {
        Instant start = Instant.now().minus(Duration.ofDays(2));
        List<Measurement> history = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            history.add(TestData.measurement(i, 7.4 + (i % 2) * 0.02, start.plus(Duration.ofHours(i)), pool));
        }
        Measurement spike = TestData.measurement(99, 8.6, start.plus(Duration.ofHours(11)).plus(Duration.ofMinutes(20)), pool);
        List<String> types = detector.detect(spike, history, pool).stream().map(Anomaly::type).toList();
        assertThat(types).containsExactlyInAnyOrder("OUT_OF_RANGE", "SUDDEN_CHANGE", "STATISTICAL_OUTLIER");
    }

    @Test
    void stableHistoryProducesNoAnomalies() {
        Instant start = Instant.now().minus(Duration.ofDays(3));
        List<Measurement> history = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            history.add(TestData.measurement(i, 7.4 + (i % 3) * 0.02, start.plus(Duration.ofHours(3L * i)), pool));
        }
        assertThat(detector.scan(history, pool)).isEmpty();
    }

    @Test
    void trendNeedsMinimumDataAndDetectsDirection() {
        assertThat(new TrendAnalyzer().analyze(List.of(), List.of()).enoughData()).isFalse();
        Instant start = Instant.now().minus(Duration.ofDays(6));
        List<Measurement> rising = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            rising.add(TestData.measurement(i, 7.0 + 0.1 * i, start.plus(Duration.ofDays(i)), pool));
        }
        TrendResult trend = new TrendAnalyzer().analyze(rising, List.of());
        assertThat(trend.direction()).isEqualTo("RISING");
        assertThat(trend.slopePerDay()).isCloseTo(0.1, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void forecasterRefusesWithoutEnoughHistory() {
        Instant start = Instant.now().minus(Duration.ofHours(10));
        List<Measurement> few = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            few.add(TestData.measurement(i, 7.2, start.plus(Duration.ofMinutes(40L * i)), pool));
        }
        Forecast forecast = new Forecaster().forecast(few, 7, Instant.now());
        assertThat(forecast.available()).isFalse();
        assertThat(forecast.reason()).contains("días");
    }

    @Test
    void forecasterProjectsTrendWithUncertainty() {
        Instant now = Instant.now();
        Instant start = now.minus(Duration.ofDays(10));
        List<Measurement> history = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            double noise = (i % 2 == 0 ? 0.03 : -0.03);
            history.add(TestData.measurement(i, 7.0 + 0.05 * (i / 2.0) + noise, start.plus(Duration.ofHours(12L * i)), pool));
        }
        Forecast forecast = new Forecaster().forecast(history, 7, now);
        assertThat(forecast.available()).isTrue();
        assertThat(forecast.horizonDays()).isEqualTo(7);
        Forecast.Point last = forecast.points().get(6);
        assertThat(last.predicted()).isGreaterThan(7.5);
        assertThat(last.lower()).isLessThan(last.predicted());
        assertThat(last.upper()).isGreaterThan(last.predicted());
        double firstWidth = forecast.points().get(0).upper() - forecast.points().get(0).lower();
        assertThat(last.upper() - last.lower()).isGreaterThanOrEqualTo(firstWidth);
    }
}
