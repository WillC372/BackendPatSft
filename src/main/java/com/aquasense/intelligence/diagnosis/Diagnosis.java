package com.aquasense.intelligence.diagnosis;

import java.util.List;

/**
 * Structured diagnosis. The AI must return exactly this shape (validated by {@link DiagnosisValidator});
 * the rule-based diagnosis produces the same structure, so the UI does not depend on who generated it.
 * {@code citedSourceIds} are ids of guideline fragments the system provided in the facts.
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
