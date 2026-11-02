package com.aquasense.reporting.api;

import com.aquasense.reporting.domain.ReportFormat;
import com.aquasense.reporting.domain.ReportRecord;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record ReportRequest(@NotNull UUID profileId, Instant from, Instant to, ReportFormat format,
                                Boolean includeChart, Boolean includeSimulated) {
    }

    public record ReportSummary(UUID id, UUID profileId, String title, ReportFormat format, Instant periodFrom,
                                Instant periodTo, int measurementCount, Instant createdAt) {

        public static ReportSummary from(ReportRecord r) {
            return new ReportSummary(r.getId(), r.getProfileId(), r.getTitle(), r.getFormat(), r.getPeriodFrom(),
                    r.getPeriodTo(), r.getMeasurementCount(), r.getCreatedAt());
        }
    }
}
