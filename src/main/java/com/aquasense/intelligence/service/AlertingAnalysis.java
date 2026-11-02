package com.aquasense.intelligence.service;

import com.aquasense.intelligence.domain.Alert;
import com.aquasense.intelligence.engine.AnomalyFinding;
import com.aquasense.intelligence.engine.Severity;
import com.aquasense.intelligence.repository.AlertRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class AlertingAnalysis implements AnalysisUseCase {

    private final AnalysisUseCase delegate;
    private final AlertRepository alerts;

    public AlertingAnalysis(AnalysisUseCase delegate, AlertRepository alerts) {
        this.delegate = delegate;
        this.alerts = alerts;
    }

    @Override
    public AnalysisResult run(AnalysisCommand command) {
        AnalysisResult result = delegate.run(command);
        int created = 0;
        for (AnomalyFinding f : result.report().anomalies()) {
            if (f.severity() == Severity.INFO) {
                continue;
            }
            String type = f.type().name();
            if (alerts.existsByOwnerIdAndTypeAndMeasuredAt(command.ownerId(), type, f.measuredAt())) {
                continue;
            }
            alerts.save(new Alert(command.ownerId(), result.analysisId(), type, f.severity(), f.measuredAt(),
                    BigDecimal.valueOf(f.ph()).setScale(2, RoundingMode.HALF_UP), f.message(), f.explanation()));
            created++;
        }
        return result.withAlerts(created);
    }
}
