package com.aquasense.intelligence.api;

import com.aquasense.intelligence.domain.Alert;
import com.aquasense.intelligence.domain.AnalysisRecord;
import com.aquasense.intelligence.engine.Severity;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class IntelligenceDtos {

    private IntelligenceDtos() {
    }

    public record AnalysisRequest(@NotNull UUID profileId, Instant from, Instant to, Boolean includeSimulated) {
    }

    public record ScenarioRequest(
            @NotNull UUID profileId,
            @DecimalMin("0") @DecimalMax("14") BigDecimal ph,
            @DecimalMin("-14") @DecimalMax("14") BigDecimal delta) {
    }

    public record AnalysisSummary(UUID id, UUID profileId, Instant createdAt, Instant from, Instant to,
                                  int measurementCount, boolean sufficientData) {

        public static AnalysisSummary from(AnalysisRecord r) {
            return new AnalysisSummary(r.getId(), r.getProfileId(), r.getCreatedAt(), r.getPeriodFrom(),
                    r.getPeriodTo(), r.getMeasurementCount(), r.isSufficientData());
        }
    }

    public record AlertResponse(UUID id, String type, Severity severity, Instant measuredAt, BigDecimal ph,
                                String message, String explanation, Instant createdAt, Instant acknowledgedAt) {

        public static AlertResponse from(Alert a) {
            return new AlertResponse(a.getId(), a.getType(), a.getSeverity(), a.getMeasuredAt(), a.getPh(),
                    a.getMessage(), a.getExplanation(), a.getCreatedAt(), a.getAcknowledgedAt());
        }
    }
}
