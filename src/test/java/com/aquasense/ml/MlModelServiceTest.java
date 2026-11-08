package com.aquasense.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquasense.intelligence.analysis.Anomaly;
import com.aquasense.intelligence.ml.FeatureExtractor;
import com.aquasense.intelligence.ml.MlAnomalyRule;
import com.aquasense.intelligence.ml.MlInsightsService;
import com.aquasense.intelligence.ml.MlModelService;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementRepository;
import com.aquasense.measurement.MeasurementSource;
import com.aquasense.measurement.validation.ValidationStatus;
import com.aquasense.profile.PhClassifier;
import com.aquasense.profile.WaterProfile;
import com.aquasense.support.TestData;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MlModelServiceTest {

    private final WaterProfile pool = TestData.pool();
    private final PhClassifier classifier = new PhClassifier();

    private Measurement m(long id, double ph, Instant at, MeasurementSource source) {
        var c = classifier.classify(ph, pool);
        Measurement m = new Measurement(5L, null, 1L, ph, null, at, source, c.nature(), c.status(), ValidationStatus.VALID, null, null);
        try {
            Field f = Measurement.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(m, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return m;
    }

    /** 12 días, cuatro mediciones diarias con un ciclo suave: más alto por la tarde, más bajo de madrugada. */
    private List<Measurement> history() {
        List<Measurement> list = new ArrayList<>();
        Instant start = Instant.now().minus(Duration.ofDays(12)).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        long id = 1;
        for (int day = 0; day < 12; day++) {
            for (int hour : new int[] {6, 12, 18, 23}) {
                double ph = 7.3 + 0.1 * Math.sin(2 * Math.PI * (hour - 6) / 24) + (id % 3) * 0.01;
                list.add(m(id++, ph, start.plus(Duration.ofDays(day)).plus(Duration.ofHours(hour)), MeasurementSource.MANUAL));
            }
        }
        return list;
    }

    @Test
    void doesNotTrainWithoutEnoughHistory() {
        MeasurementRepository repo = mock(MeasurementRepository.class);
        when(repo.findTop2000ByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(eq(5L), any(), any())).thenReturn(history().subList(0, 10));
        MlModelService.Training training = new MlModelService(repo).model(5L, false);
        assertThat(training.trained()).isFalse();
        assertThat(training.reason()).contains("30");
    }

    @Test
    void learnsTheUsualBehaviourAndFlagsAnUnusualReading() {
        MeasurementRepository repo = mock(MeasurementRepository.class);
        List<Measurement> history = history();
        when(repo.findTop2000ByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(eq(5L), any(), any())).thenReturn(history);
        MlModelService service = new MlModelService(repo);
        MlAnomalyRule rule = new MlAnomalyRule(service);

        Measurement last = history.get(history.size() - 1);
        Measurement normal = m(999, 7.31, last.getMeasuredAt().plus(Duration.ofHours(7)), MeasurementSource.MANUAL);
        assertThat(rule.evaluate(normal, history, pool)).isEmpty();

        Measurement wrongHour = m(998, 7.22, last.getMeasuredAt().plus(Duration.ofHours(7)), MeasurementSource.MANUAL);
        assertThat(rule.evaluate(wrongHour, history, pool)).as("7.22 a las 06:00 nunca ocurre en este historial").isPresent();

        Measurement spike = m(1000, 6.4, last.getMeasuredAt().plus(Duration.ofMinutes(20)), MeasurementSource.MANUAL);
        Optional<Anomaly> anomaly = rule.evaluate(spike, history, pool);
        assertThat(anomaly).isPresent();
        assertThat(anomaly.get().type()).isEqualTo(MlAnomalyRule.TYPE);
        assertThat(anomaly.get().message()).contains("inusual para tu historial");

        Measurement simulated = m(1001, 4.0, last.getMeasuredAt().plus(Duration.ofMinutes(30)), MeasurementSource.SIMULATED);
        assertThat(rule.evaluate(simulated, history, pool)).isEmpty();
        verify(repo, times(1)).findTop2000ByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(eq(5L), any(), any());
        assertThat(anomaly.get().message()).contains("Valor de pH");

        service.onMeasurementRecorded(spike, pool);
        service.model(5L, false);
        verify(repo, times(2)).findTop2000ByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(eq(5L), any(), any());
    }

    @Test
    void insightsReportScoresHourlyPatternAndExplanations() {
        MeasurementRepository repo = mock(MeasurementRepository.class);
        List<Measurement> history = new ArrayList<>(history());
        Measurement last = history.get(history.size() - 1);
        history.add(m(500, 6.3, last.getMeasuredAt().plus(Duration.ofMinutes(15)), MeasurementSource.MANUAL));
        when(repo.findTop2000ByUserIdAndMeasuredAtBetweenOrderByMeasuredAtAsc(eq(5L), any(), any())).thenReturn(history);
        var insights = new MlInsightsService(new MlModelService(repo)).insights(5L, 30, false);
        assertThat(insights.trained()).isTrue();
        assertThat(insights.treeCount()).isEqualTo(100);
        assertThat(insights.points()).hasSize(history.size());
        assertThat(insights.anomalies()).isNotEmpty();
        assertThat(insights.anomalies().get(0).id()).isEqualTo(500L);
        assertThat(insights.anomalies().get(0).factors()).isNotEmpty();
        assertThat(insights.hourlyPattern()).hasSize(24);
        assertThat(insights.hourlyPattern().get(18).count()).isEqualTo(12);
    }

    @Test
    void featuresUseOnlyPastDataAndEncodeTheHourCyclically() {
        List<Measurement> history = history();
        double[] first = FeatureExtractor.vector(history, 0, false, 0);
        assertThat(first[1]).isZero();
        double[] second = FeatureExtractor.vector(history, 1, false, 0);
        assertThat(second[1]).isCloseTo(history.get(1).getPh() - history.get(0).getPh(), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(Math.hypot(second[4], second[5])).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
