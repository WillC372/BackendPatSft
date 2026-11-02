package com.aquasense.reporting.service;

import com.aquasense.reporting.repository.ReportRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReportQuota {

    private final ReportRepository reports;

    public ReportQuota(ReportRepository reports) {
        this.reports = reports;
    }

    public long usedThisMonth(UUID ownerId) {
        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        return reports.countByOwnerIdAndCreatedAtAfter(ownerId, startOfMonth);
    }
}
