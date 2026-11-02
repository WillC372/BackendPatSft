package com.aquasense.intelligence.service;

import com.aquasense.billing.domain.PlanLimits;
import com.aquasense.billing.service.SubscriptionService;
import com.aquasense.intelligence.engine.AnalysisReport;
import com.aquasense.intelligence.engine.ForecastResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class PlanRestrictedAnalysis implements AnalysisUseCase {

    private final AnalysisUseCase delegate;
    private final SubscriptionService subscriptions;

    public PlanRestrictedAnalysis(AnalysisUseCase delegate, SubscriptionService subscriptions) {
        this.delegate = delegate;
        this.subscriptions = subscriptions;
    }

    @Override
    public AnalysisResult run(AnalysisCommand command) {
        PlanLimits limits = subscriptions.limitsFor(command.ownerId());
        AnalysisCommand effective = command;
        List<String> notes = new ArrayList<>();
        if (limits.maxAnalysisDays() != null) {
            Instant earliest = Instant.now().minus(limits.maxAnalysisDays(), ChronoUnit.DAYS);
            if (command.from() == null || command.from().isBefore(earliest)) {
                effective = new AnalysisCommand(command.ownerId(), command.profileId(), earliest, command.to(),
                        command.includeSimulated());
                notes.add("Plan gratuito: el análisis se limita a los últimos " + limits.maxAnalysisDays() + " días.");
            }
        }
        AnalysisResult result = delegate.run(effective);
        AnalysisReport report = result.report();
        ForecastResult forecast = report.forecast();
        if (!limits.forecastEnabled()) {
            forecast = ForecastResult.unavailable("Las predicciones están disponibles en el plan Premium.");
            notes.add("Plan gratuito: sin predicciones.");
        }
        if (notes.isEmpty()) {
            return result;
        }
        List<String> limitations = new ArrayList<>(report.limitations());
        limitations.addAll(notes);
        AnalysisReport restricted = new AnalysisReport(report.profileName(), report.measurementCount(),
                report.simulatedCount(), report.sufficientData(), report.statistics(), report.anomalies(),
                report.trend(), forecast, report.recommendations(), limitations, report.disclaimer());
        return new AnalysisResult(result.analysisId(), result.createdAt(), result.alertsCreated(), restricted);
    }
}
