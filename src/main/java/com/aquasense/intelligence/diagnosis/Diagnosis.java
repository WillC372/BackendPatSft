package com.aquasense.intelligence.diagnosis;

import java.util.List;

/**
 * Diagnóstico estructurado. La IA debe devolver exactamente esta forma (validada por {@link DiagnosisValidator});
 * el diagnóstico por reglas produce la misma estructura, así la interfaz no depende de quién lo generó.
 * {@code citedSourceIds} son ids de fragmentos normativos que el sistema entregó en los hechos.
 */
public record Diagnosis(RiskLevel riskLevel, String summary, List<Finding> findings, List<Cause> probableCauses,
                        List<Action> actions, List<String> dataGaps, double confidence, int nextMeasurementHours,
                        List<String> citedSourceIds) {

    public Diagnosis {
        citedSourceIds = citedSourceIds == null ? List.of() : List.copyOf(citedSourceIds);
    }

    public record Finding(String title, String evidence, Severity severity) {
    }

    public record Cause(String cause, Likelihood likelihood, String evidence) {
    }

    public record Action(String action, int priority, String timeframe) {
    }

    public enum Severity { INFO, CAUTION, CRITICAL }

    public enum Likelihood { LOW, MEDIUM, HIGH }
}
