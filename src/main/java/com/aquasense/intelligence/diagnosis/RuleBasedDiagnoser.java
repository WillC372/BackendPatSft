package com.aquasense.intelligence.diagnosis;

import com.aquasense.intelligence.analysis.Anomaly;
import com.aquasense.profile.PhStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Diagnóstico determinista con la misma estructura que el de la IA. Se usa cuando ningún
 * proveedor responde o su respuesta no supera la validación.
 */
@Component
public class RuleBasedDiagnoser {

    /** El diagnóstico por reglas cita los fragmentos mejor clasificados por el recuperador. */
    static final int RULE_CITATIONS = 3;

    public Diagnosis diagnose(DiagnosisFacts facts) {
        if (!facts.hasData()) {
            return new Diagnosis(RiskLevel.LOW, "No hay mediciones en el periodo; no es posible diagnosticar.",
                List.of(new Diagnosis.Finding("Sin mediciones", "0 mediciones en los últimos " + facts.periodDays() + " días.",
                    Diagnosis.Severity.INFO)),
                List.of(), List.of(new Diagnosis.Action("Realizar una medición con el medidor o registrarla manualmente.", 1, "Hoy")),
                List.of("No hay mediciones en el periodo seleccionado."), 0.0, 24, List.of());
        }
        var latest = facts.latest();
        long criticalAnomalies = facts.anomalies().stream().filter(a -> a.severity() == Anomaly.Severity.CRITICAL).count();
        boolean moving = facts.trend().enoughData() && !"STABLE".equals(facts.trend().direction());
        RiskLevel risk;
        if (latest.status() == PhStatus.CRITICAL) {
            risk = RiskLevel.CRITICAL;
        } else if (latest.status() == PhStatus.CAUTION && (moving || criticalAnomalies > 0)) {
            risk = RiskLevel.HIGH;
        } else if (latest.status() == PhStatus.CAUTION || !facts.anomalies().isEmpty() || moving) {
            risk = RiskLevel.MODERATE;
        } else {
            risk = RiskLevel.LOW;
        }

        List<Diagnosis.Finding> findings = new ArrayList<>();
        findings.add(new Diagnosis.Finding("Última medición: " + latest.status().label().toLowerCase(Locale.ROOT),
            latest.explanation(), severityOf(latest.status())));
        if (facts.trend().enoughData()) {
            findings.add(new Diagnosis.Finding("Tendencia " + facts.trend().directionLabel().toLowerCase(Locale.ROOT),
                facts.trend().message(), moving ? Diagnosis.Severity.CAUTION : Diagnosis.Severity.INFO));
        }
        facts.anomalies().stream().limit(3).forEach(a -> findings.add(new Diagnosis.Finding(a.title(), a.message(),
            a.severity() == Anomaly.Severity.CRITICAL ? Diagnosis.Severity.CRITICAL : Diagnosis.Severity.CAUTION)));
        if (facts.includesSimulated()) {
            findings.add(new Diagnosis.Finding("Datos simulados", facts.simulatedCount() + " de " + facts.count()
                + " mediciones provienen del simulador.", Diagnosis.Severity.INFO));
        }

        List<Diagnosis.Cause> causes = facts.baseAdvice().possibleCauses().stream().limit(4)
            .map(c -> new Diagnosis.Cause(c, Diagnosis.Likelihood.MEDIUM, "Causa frecuente para el perfil " + facts.profile().getName() + "."))
            .toList();
        List<Diagnosis.Action> actions = new ArrayList<>();
        List<String> advice = facts.baseAdvice().actions();
        for (int i = 0; i < Math.min(5, advice.size()); i++) {
            actions.add(new Diagnosis.Action(advice.get(i), i == 0 ? 1 : 2, risk.ordinal() >= RiskLevel.HIGH.ordinal() ? "Hoy" : "Esta semana"));
        }
        if (facts.baseAdvice().needsExpert()) {
            actions.add(new Diagnosis.Action(facts.baseAdvice().expertNote(), 2, "Si el valor persiste"));
        }
        if (actions.isEmpty()) {
            actions.add(new Diagnosis.Action("Mantener la frecuencia de medición.", 3, "Continuo"));
        }

        List<String> gaps = new ArrayList<>();
        if (facts.count() < 10) {
            gaps.add("Solo hay " + facts.count() + " mediciones; con 10 o más el análisis es más confiable.");
        }
        if (!facts.forecast().available()) {
            gaps.add("Predicción no disponible: " + facts.forecast().reason());
        }
        if (facts.includesSimulated()) {
            gaps.add("Parte de las mediciones del periodo proviene del simulador y no representa agua real.");
        }
        double confidence = Math.min(DiagnosisValidator.maxConfidence(facts), 0.5 + facts.count() * 0.02);
        int next = switch (risk) {
            case CRITICAL -> 2;
            case HIGH -> 6;
            case MODERATE -> 24;
            case LOW -> 48;
        };
        String summary = String.format(Locale.US, "Riesgo %s. Última medición pH %.2f (%s) para %s. %s",
            risk.label().toLowerCase(Locale.ROOT), latest.ph(), latest.status().label().toLowerCase(Locale.ROOT),
            facts.profile().getName(), facts.baseAdvice().summary());
        return new Diagnosis(risk, summary, findings.subList(0, Math.min(6, findings.size())), causes,
            actions.subList(0, Math.min(6, actions.size())), gaps.subList(0, Math.min(5, gaps.size())),
            Math.round(confidence * 100) / 100.0, next,
            facts.guidelineIds().subList(0, Math.min(RULE_CITATIONS, facts.guidelineIds().size())));
    }

    private static Diagnosis.Severity severityOf(PhStatus status) {
        return switch (status) {
            case OPTIMAL -> Diagnosis.Severity.INFO;
            case CAUTION -> Diagnosis.Severity.CAUTION;
            case CRITICAL -> Diagnosis.Severity.CRITICAL;
        };
    }
}
