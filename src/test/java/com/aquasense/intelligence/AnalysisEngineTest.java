package com.aquasense.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquasense.intelligence.detect.AnomalyDetectionChain;
import com.aquasense.intelligence.engine.AnalysisInput;
import com.aquasense.intelligence.engine.AnalysisReport;
import com.aquasense.intelligence.engine.AnomalyFinding;
import com.aquasense.intelligence.engine.CoreAnalysisEngine;
import com.aquasense.intelligence.engine.FindingType;
import com.aquasense.intelligence.engine.Forecaster;
import com.aquasense.intelligence.engine.Sample;
import com.aquasense.intelligence.engine.Severity;
import com.aquasense.intelligence.engine.TrendDirection;
import com.aquasense.intelligence.strategy.AnalysisStrategies;
import com.aquasense.waterprofile.domain.WaterType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisEngineTest {

    private static final Instant START = Instant.parse("2026-10-20T00:00:00Z");

    private final CoreAnalysisEngine engine = new CoreAnalysisEngine(new AnalysisStrategies(),
            new AnomalyDetectionChain());

    private static List<Sample> series(double[] values, Duration step, boolean simulated) {
        List<Sample> out = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            out.add(new Sample(START.plus(step.multipliedBy(i)), values[i], simulated));
        }
        return out;
    }

    private AnalysisInput general(List<Sample> samples) {
        return new AnalysisInput("Análisis general", WaterType.GENERAL, 6.5, 8.5, 6.0, 9.0, samples);
    }

    private AnalysisInput of(WaterType type, double[] range, List<Sample> samples) {
        return new AnalysisInput("Perfil", type, range[0], range[1], range[2], range[3], samples);
    }

    @Test
    void detectsDropFromHabitualValueDescribedInTheRequirements() {
        double[] values = {7.1, 7.12, 7.08, 7.11, 7.09, 7.1, 7.13, 7.1, 7.11, 5.8, 5.82, 5.8};
        AnalysisReport report = engine.analyze(general(series(values, Duration.ofHours(6), false)));
        assertThat(report.anomalies()).isNotEmpty();
        assertThat(report.anomalies()).anyMatch(f -> f.type() == FindingType.STATISTICAL_OUTLIER);
        assertThat(report.anomalies()).anyMatch(f -> f.type() == FindingType.OUT_OF_RANGE);
        assertThat(report.anomalies()).anyMatch(f -> f.severity() == Severity.CRITICAL);
        assertThat(report.anomalies()).allSatisfy(f -> assertThat(f.explanation()).isNotBlank());
        assertThat(report.anomalies().stream().filter(f -> f.type() == FindingType.STATISTICAL_OUTLIER)
                .findFirst().orElseThrow().explanation()).contains("no confirma").contains("7.10");
    }

    @Test
    void stableSeriesProducesNoAnomaliesNorTrend() {
        double[] values = {7.1, 7.12, 7.08, 7.11, 7.09, 7.1, 7.13, 7.1, 7.11, 7.09, 7.1, 7.12};
        AnalysisReport report = engine.analyze(general(series(values, Duration.ofHours(8), false)));
        assertThat(report.anomalies()).isEmpty();
        assertThat(report.trend().direction()).isEqualTo(TrendDirection.STABLE);
    }

    @Test
    void detectsDecliningTrendWithKnownSlopeAndForecastsWithInterval() {
        double[] values = new double[10];
        for (int i = 0; i < values.length; i++) {
            values[i] = 7.6 - 0.2 * i + (i % 2 == 0 ? 0.01 : -0.01);
        }
        AnalysisReport report = engine.analyze(general(series(values, Duration.ofDays(1), false)));
        assertThat(report.trend().direction()).isEqualTo(TrendDirection.FALLING);
        assertThat(report.trend().slopePerDay()).isBetween(-0.21, -0.19);
        assertThat(report.trend().rSquared()).isGreaterThan(0.95);
        assertThat(report.forecast().available()).isTrue();
        assertThat(report.forecast().horizonHours()).isEqualTo(72);
        assertThat(report.forecast().points()).hasSize(3).allSatisfy(p -> {
            assertThat(p.lower()).isLessThan(p.predicted());
            assertThat(p.upper()).isGreaterThan(p.predicted());
        });
        assertThat(report.forecast().points().get(2).predicted()).isLessThan(values[9]);
        assertThat(report.forecast().limitations()).contains("No considera");
    }

    @Test
    void uncertaintyGrowsFurtherIntoTheFuture() {
        double[] values = {7.5, 7.3, 7.35, 7.1, 7.0, 6.9, 6.95, 6.7, 6.6, 6.5};
        var points = engine.analyze(general(series(values, Duration.ofHours(12), false))).forecast().points();
        double first = points.get(0).upper() - points.get(0).lower();
        double last = points.get(2).upper() - points.get(2).lower();
        assertThat(last).isGreaterThan(first);
    }

    @Test
    void refusesToForecastWithoutEnoughDataOrPattern() {
        assertThat(Forecaster.forecast(series(new double[] {7.0, 7.1, 7.0}, Duration.ofDays(1), false)).available())
                .isFalse();
        assertThat(Forecaster.forecast(series(new double[] {7, 7.1, 7, 7.2, 7, 7.1, 7, 7.1}, Duration.ofHours(2),
                false)).reason()).contains("48 horas");
        double[] noise = {7.0, 7.9, 6.6, 8.1, 6.8, 7.7, 6.7, 8.0, 6.9, 7.8};
        var result = Forecaster.forecast(series(noise, Duration.ofDays(1), false));
        assertThat(result.available()).isFalse();
        assertThat(result.reason()).contains("patrón lineal");
    }

    @Test
    void reportsInsufficientDataHonestly() {
        AnalysisReport report = engine.analyze(general(series(new double[] {7.0, 7.1}, Duration.ofHours(1), false)));
        assertThat(report.sufficientData()).isFalse();
        assertThat(report.anomalies()).isEmpty();
        assertThat(report.forecast().available()).isFalse();
        assertThat(report.limitations()).anyMatch(l -> l.contains("No hay datos suficientes"));
        assertThat(report.recommendations()).anyMatch(r -> r.contains("más mediciones"));
    }

    @Test
    void strategyChangesSensitivityByWaterType() {
        double[] values = {7.0, 7.0, 7.05, 7.0, 7.0, 7.02, 7.0, 7.0, 7.0, 7.4};
        List<Sample> samples = series(values, Duration.ofHours(1), false);
        double[] range = {6.0, 8.0, 5.0, 9.0};
        long aquarium = engine.analyze(of(WaterType.AQUARIUM, range, samples)).anomalies().stream()
                .filter(f -> f.type() == FindingType.ABRUPT_CHANGE).count();
        long agriculture = engine.analyze(of(WaterType.AGRICULTURE, range, samples)).anomalies().stream()
                .filter(f -> f.type() == FindingType.ABRUPT_CHANGE).count();
        assertThat(aquarium).isEqualTo(1);
        assertThat(agriculture).isZero();
    }

    @Test
    void simulatedDataIsDeclaredAsLimitation() {
        double[] values = {7.0, 7.1, 7.0, 7.1, 7.0, 7.1};
        AnalysisReport report = engine.analyze(general(series(values, Duration.ofHours(4), true)));
        assertThat(report.simulatedCount()).isEqualTo(6);
        assertThat(report.limitations()).anyMatch(l -> l.contains("SIMULACIÓN"));
    }

    @Test
    void recommendationsNeverClaimWaterIsSafe() {
        double[] values = {7.0, 7.1, 7.0, 7.1, 7.0, 7.1, 7.0, 7.1};
        AnalysisReport report = engine.analyze(of(WaterType.DRINKING, new double[] {6.5, 8.5, 6.0, 9.0},
                series(values, Duration.ofHours(5), false)));
        String all = String.join(" ", report.recommendations()).toLowerCase();
        assertThat(all).doesNotContain("es segura").doesNotContain("es potable").contains("laboratorio");
        assertThat(report.disclaimer()).contains("no demuestra");
    }

    @Test
    void abruptChangeIsFlaggedWithExplanation() {
        double[] values = {7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 4.0};
        List<AnomalyFinding> findings = engine.analyze(general(series(values, Duration.ofMinutes(30), false)))
                .anomalies();
        assertThat(findings).anyMatch(f -> f.type() == FindingType.ABRUPT_CHANGE
                && f.explanation().contains("error del sensor"));
    }
}
