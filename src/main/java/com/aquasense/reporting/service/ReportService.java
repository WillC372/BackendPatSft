package com.aquasense.reporting.service;

import com.aquasense.billing.domain.PlanLimits;
import com.aquasense.billing.service.LimitGuard;
import com.aquasense.billing.service.SubscriptionService;
import com.aquasense.device.domain.SourceKind;
import com.aquasense.identity.domain.User;
import com.aquasense.identity.repository.UserRepository;
import com.aquasense.intelligence.engine.AnalysisReport;
import com.aquasense.intelligence.service.AnalysisCommand;
import com.aquasense.intelligence.service.AnalysisUseCase;
import com.aquasense.measurement.domain.Measurement;
import com.aquasense.measurement.service.MeasurementService;
import com.aquasense.reporting.domain.ChartSection;
import com.aquasense.reporting.domain.Report;
import com.aquasense.reporting.domain.ReportBuilder;
import com.aquasense.reporting.domain.ReportFormat;
import com.aquasense.reporting.domain.ReportRecord;
import com.aquasense.reporting.render.ReportRendererFactory;
import com.aquasense.reporting.repository.ReportRepository;
import com.aquasense.shared.DomainException;
import com.aquasense.waterprofile.domain.WaterProfile;
import com.aquasense.waterprofile.service.WaterProfileService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private static final int MAX_ROWS = 100;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Bogota"));

    private final ReportRepository reports;
    private final UserRepository users;
    private final WaterProfileService profiles;
    private final MeasurementService measurements;
    private final AnalysisUseCase analysis;
    private final SubscriptionService subscriptions;
    private final ReportQuota quota;
    private final ReportRendererFactory renderers;

    public ReportService(ReportRepository reports, UserRepository users, WaterProfileService profiles,
                         MeasurementService measurements, @Qualifier("restrictedAnalysis") AnalysisUseCase analysis,
                         SubscriptionService subscriptions, ReportQuota quota, ReportRendererFactory renderers) {
        this.reports = reports;
        this.users = users;
        this.profiles = profiles;
        this.measurements = measurements;
        this.analysis = analysis;
        this.subscriptions = subscriptions;
        this.quota = quota;
        this.renderers = renderers;
    }

    @Transactional
    public ReportRecord generate(UUID ownerId, UUID profileId, Instant from, Instant to, ReportFormat format,
                                 boolean includeChart, boolean includeSimulated) {
        PlanLimits limits = subscriptions.limitsFor(ownerId);
        if (quota.usedThisMonth(ownerId) >= limits.maxReportsPerMonth()) {
            throw LimitGuard.denied("Alcanzó el máximo de " + limits.maxReportsPerMonth() + " informes por mes.");
        }
        if ((format == ReportFormat.HTML || includeChart) && !limits.advancedReports()) {
            throw LimitGuard.denied("El formato HTML y las gráficas en informes son exclusivos de Premium.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD",
                    "La fecha inicial debe ser anterior a la final.");
        }
        Instant effectiveFrom = from;
        if (limits.maxReportDays() != null) {
            Instant earliest = Instant.now().minus(limits.maxReportDays(), ChronoUnit.DAYS);
            if (effectiveFrom == null || effectiveFrom.isBefore(earliest)) {
                effectiveFrom = earliest;
            }
        }
        User user = users.findById(ownerId).orElseThrow();
        WaterProfile profile = profiles.get(profileId, ownerId);
        List<Measurement> data = measurements.forAnalysis(ownerId, profileId, effectiveFrom, to, includeSimulated,
                1000);
        AnalysisReport result = analysis.run(new AnalysisCommand(ownerId, profileId, effectiveFrom, to,
                includeSimulated)).report();

        List<Measurement> newestFirst = new ArrayList<>(data);
        Collections.reverse(newestFirst);
        List<List<String>> rows = newestFirst.stream().limit(MAX_ROWS).map(m -> List.of(
                FORMAT.format(m.getMeasuredAt()), m.getPh().toPlainString(),
                m.getSourceKind() == SourceKind.SIMULATED ? "SIMULACIÓN" : "Real", m.getRangeStatus().name(),
                m.getValidationStatus().name())).toList();

        ReportBuilder builder = new ReportBuilder()
                .title("Informe de calidad del agua — " + profile.getName())
                .general(user.getDisplayName(), user.getEmail(), profile.getName(), effectiveFrom, to, result)
                .statistics(result)
                .anomalies(result)
                .trendAndForecast(result);
        if (includeChart) {
            builder.chart(data.stream().map(m -> new ChartSection.Point(m.getMeasuredAt(),
                    m.getPh().doubleValue())).toList(), profile.getOptimalMin().doubleValue(),
                    profile.getOptimalMax().doubleValue());
        }
        Report report = builder.measurements(rows, data.size()).conclusions(result).build();
        String content = renderers.forFormat(format).render(report);
        return reports.save(new ReportRecord(ownerId, profileId, report.title(), format, effectiveFrom, to,
                data.size(), content));
    }

    @Transactional(readOnly = true)
    public List<ReportRecord> list(UUID ownerId, int size) {
        return reports.findByOwnerIdOrderByCreatedAtDesc(ownerId, PageRequest.of(0, Math.min(Math.max(size, 1), 50)));
    }

    @Transactional(readOnly = true)
    public ReportRecord get(UUID id, UUID ownerId) {
        return reports.findByIdAndOwnerId(id, ownerId).orElseThrow(() ->
                new DomainException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "El informe no existe."));
    }
}
