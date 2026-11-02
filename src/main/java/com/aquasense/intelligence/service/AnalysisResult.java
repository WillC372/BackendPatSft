package com.aquasense.intelligence.service;

import com.aquasense.intelligence.engine.AnalysisReport;
import java.time.Instant;
import java.util.UUID;

public record AnalysisResult(UUID analysisId, Instant createdAt, int alertsCreated, AnalysisReport report) {

    public AnalysisResult withPersistence(UUID id, Instant when) {
        return new AnalysisResult(id, when, alertsCreated, report);
    }

    public AnalysisResult withAlerts(int count) {
        return new AnalysisResult(analysisId, createdAt, count, report);
    }
}
