package com.aquasense.intelligence.diagnosis;

import com.aquasense.knowledge.Citation;
import java.time.Instant;
import java.util.List;

public record DiagnosisResponse(Long id, Instant createdAt, String source, String sourceLabel, String provider,
                                String model, RiskLevel riskLevel, String riskLabel, String summary,
                                List<Diagnosis.Finding> findings, List<Diagnosis.Cause> probableCauses,
                                List<Diagnosis.Action> actions, List<String> dataGaps, double confidence,
                                int nextMeasurementHours, int measurementCount, int periodDays, String profileName,
                                boolean includesSimulated, List<String> adjustments, String note, String disclaimer,
                                List<Citation> citations, String coverageNote) {

    public DiagnosisResponse withId(Long newId) {
        return new DiagnosisResponse(newId, createdAt, source, sourceLabel, provider, model, riskLevel, riskLabel, summary,
            findings, probableCauses, actions, dataGaps, confidence, nextMeasurementHours, measurementCount, periodDays,
            profileName, includesSimulated, adjustments, note, disclaimer, citations, coverageNote);
    }
}
