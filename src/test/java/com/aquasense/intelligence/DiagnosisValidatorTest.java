package com.aquasense.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquasense.intelligence.advice.Advice;
import com.aquasense.intelligence.analysis.Forecast;
import com.aquasense.intelligence.analysis.TrendResult;
import com.aquasense.intelligence.diagnosis.Diagnosis;
import com.aquasense.intelligence.diagnosis.DiagnosisFacts;
import com.aquasense.intelligence.diagnosis.DiagnosisSchema;
import com.aquasense.intelligence.diagnosis.DiagnosisValidator;
import com.aquasense.intelligence.diagnosis.RiskLevel;
import com.aquasense.intelligence.diagnosis.RuleBasedDiagnoser;
import com.aquasense.measurement.Measurement;
import com.aquasense.profile.PhClassifier;
import com.aquasense.profile.WaterProfile;
import com.aquasense.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiagnosisValidatorTest {

    private final DiagnosisValidator validator = new DiagnosisValidator(new ObjectMapper());
    private final WaterProfile pool = TestData.pool();

    private static final String VALID = """
        {"riskLevel":"LOW","summary":"Todo en rango.",
         "findings":[{"title":"pH estable","evidence":"7.4 el 2026-11-06","severity":"INFO"}],
         "probableCauses":[],
         "actions":[{"action":"Medir mañana","priority":2,"timeframe":"En 24 h"},{"action":"Revisar cloro","priority":1,"timeframe":"Hoy"}],
         "dataGaps":[],"confidence":0.95,"nextMeasurementHours":48}""";

    private DiagnosisFacts facts(double lastPh, int count) {
        List<Measurement> list = new ArrayList<>();
        Instant start = Instant.now().minus(Duration.ofDays(3));
        for (int i = 0; i < count; i++) {
            list.add(TestData.measurement(i, i == count - 1 ? lastPh : 7.4, start.plus(Duration.ofHours(4L * i)), pool));
        }
        var latest = new PhClassifier().classify(lastPh, pool);
        var trend = new TrendResult(false, "", count, 0, 0, 0, 0, 0, "UNKNOWN", "", null, null, Map.of());
        var forecast = new Forecast(false, "pocos datos", "", count, 0, 0, 0, 0, List.of(), List.of());
        return new DiagnosisFacts(pool, 7, list, latest, trend, List.of(), forecast,
            new Advice("Resumen", List.of("Lluvia"), List.of("Medir de nuevo"), false, null), 0, 0, count);
    }

    @Test
    void keepsOnlyCitationsThatWereOfferedInTheFacts() {
        var kb = new com.aquasense.knowledge.GuidelineRetriever(new com.aquasense.knowledge.KnowledgeBase(new ObjectMapper()));
        DiagnosisFacts base = facts(7.4, 20);
        DiagnosisFacts withSources = new DiagnosisFacts(base.profile(), 7, base.measurements(), base.latest(), base.trend(),
            base.anomalies(), base.forecast(), base.baseAdvice(), 0, 0, 20, kb.resolve(List.of("mahc-ph-range", "mahc-testing")));
        String json = VALID.replace("\"nextMeasurementHours\":48}",
            "\"nextMeasurementHours\":48,\"citedSourceIds\":[\"mahc-ph-range\",\"epa-ph-smcl\",\"mahc-ph-range\"]}");
        var result = validator.validate(json, withSources);
        assertThat(result.diagnosis().citedSourceIds()).containsExactly("mahc-ph-range");
        assertThat(result.adjustments()).anyMatch(a -> a.contains("epa-ph-smcl"));
        assertThat(validator.validate(VALID, withSources).diagnosis().citedSourceIds()).isEmpty();
        assertThat(DiagnosisSchema.forSources(withSources.guidelineIds()).toString()).contains("mahc-testing");
    }

    @Test
    void acceptsValidResponseAndSortsActionsByPriority() {
        var result = validator.validate(VALID, facts(7.4, 20));
        assertThat(result.diagnosis().riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.diagnosis().actions().get(0).priority()).isEqualTo(1);
        assertThat(result.diagnosis().confidence()).isEqualTo(0.9);
        assertThat(result.adjustments()).hasSize(1);
    }

    @Test
    void capsConfidenceWhenThereAreFewMeasurements() {
        var result = validator.validate(VALID, facts(7.4, 3));
        assertThat(result.diagnosis().confidence()).isEqualTo(0.4);
        assertThat(result.adjustments().get(0)).contains("Confianza ajustada");
    }

    @Test
    void raisesRiskWhenLatestMeasurementIsCritical() {
        var result = validator.validate(VALID, facts(6.2, 20));
        assertThat(result.diagnosis().riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.diagnosis().nextMeasurementHours()).isLessThanOrEqualTo(12);
        assertThat(result.adjustments()).anyMatch(a -> a.contains("Riesgo elevado"));
    }

    @Test
    void rejectsInventedEnumsMissingFieldsAndNonJson() {
        assertThatThrownBy(() -> validator.validate(VALID.replace("\"LOW\"", "\"BAJO\""), facts(7.4, 20)))
            .isInstanceOf(DiagnosisValidator.InvalidDiagnosisException.class);
        assertThatThrownBy(() -> validator.validate(VALID.replace("\"findings\":[{\"title\":\"pH estable\",\"evidence\":\"7.4 el 2026-11-06\",\"severity\":\"INFO\"}]", "\"findings\":[]"), facts(7.4, 20)))
            .isInstanceOf(DiagnosisValidator.InvalidDiagnosisException.class);
        assertThatThrownBy(() -> validator.validate("El agua está bien", facts(7.4, 20)))
            .isInstanceOf(DiagnosisValidator.InvalidDiagnosisException.class);
    }

    @Test
    void ruleBasedDiagnosisHasSameStructureAndMarksCriticalValues() {
        Diagnosis diagnosis = new RuleBasedDiagnoser().diagnose(facts(6.2, 12));
        assertThat(diagnosis.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(diagnosis.findings()).isNotEmpty();
        assertThat(diagnosis.actions()).isNotEmpty();
        assertThat(diagnosis.nextMeasurementHours()).isEqualTo(2);
        String json = VALID;
        assertThat(validator.validate(json, facts(7.4, 12)).diagnosis().findings()).hasSize(1);
    }
}
