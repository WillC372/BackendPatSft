package com.aquasense.report;

import com.aquasense.alert.AlertResponse;
import com.aquasense.alert.AlertService;
import com.aquasense.intelligence.IntelligenceFacade;
import com.aquasense.intelligence.ai.AiUnavailableException;
import com.aquasense.intelligence.dto.AnomaliesResponse;
import com.aquasense.intelligence.dto.ConclusionResponse;
import com.aquasense.intelligence.dto.ForecastResponse;
import com.aquasense.intelligence.dto.RecommendationResponse;
import com.aquasense.intelligence.dto.TrendsResponse;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementService;
import com.aquasense.measurement.MeasurementSource;
import com.aquasense.profile.PhStatus;
import com.aquasense.profile.ProfileService;
import com.aquasense.audit.AuditService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("UTC"));

    private final MeasurementService measurementService;
    private final ProfileService profileService;
    private final IntelligenceFacade intelligence;
    private final AlertService alertService;
    private final AuditService auditService;

    public ReportService(MeasurementService measurementService, ProfileService profileService,
                         IntelligenceFacade intelligence, AlertService alertService, AuditService auditService) {
        this.measurementService = measurementService;
        this.profileService = profileService;
        this.intelligence = intelligence;
        this.alertService = alertService;
        this.auditService = auditService;
    }

    @Transactional
    public Report build(Long userId, int requestedDays, boolean includeAi) {
        TrendsResponse trends = intelligence.trends(userId, requestedDays);
        int days = trends.days();
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofDays(days));

        int count = 0;
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        Map<PhStatus, Integer> byStatus = new EnumMap<>(PhStatus.class);
        Map<MeasurementSource, Integer> bySource = new EnumMap<>(MeasurementSource.class);
        Iterator<Measurement> iterator = measurementService.iterate(userId, start, end);
        while (iterator.hasNext()) {
            Measurement m = iterator.next();
            count++;
            sum += m.getPh();
            min = Math.min(min, m.getPh());
            max = Math.max(max, m.getPh());
            byStatus.merge(m.getStatus(), 1, Integer::sum);
            bySource.merge(m.getSource(), 1, Integer::sum);
        }

        ReportBuilder builder = new ReportBuilder()
            .title("Informe de calidad del agua - últimos " + days + " días")
            .period(start, end)
            .profile(profileService.activeProfile(userId).getName())
            .measurementCount(count)
            .containsSimulatedData(bySource.containsKey(MeasurementSource.SIMULATED))
            .disclaimer(IntelligenceFacade.PH_DISCLAIMER);

        if (count == 0) {
            builder.section("summary", "Resumen", List.of("No hay mediciones en el periodo; no es posible generar conclusiones."));
            return builder.build();
        }

        List<String> summary = new ArrayList<>();
        summary.add(String.format(Locale.US, "Mediciones: %d. Promedio: %.2f. Mínimo: %.2f. Máximo: %.2f.", count,
            sum / count, min, max));
        summary.add(String.format("En rango óptimo: %d. En precaución: %d. Críticas: %d.",
            byStatus.getOrDefault(PhStatus.OPTIMAL, 0), byStatus.getOrDefault(PhStatus.CAUTION, 0),
            byStatus.getOrDefault(PhStatus.CRITICAL, 0)));
        summary.add(String.format("Origen: medidor físico %d, manual %d, foto con IA %d, simulador %d.",
            bySource.getOrDefault(MeasurementSource.REAL, 0), bySource.getOrDefault(MeasurementSource.MANUAL, 0),
            bySource.getOrDefault(MeasurementSource.PHOTO, 0), bySource.getOrDefault(MeasurementSource.SIMULATED, 0)));
        if (bySource.containsKey(MeasurementSource.SIMULATED)) {
            summary.add("Atención: el periodo incluye lecturas del simulador, que no representan agua real.");
        }
        builder.section("summary", "Resumen del periodo", summary);
        builder.section("trend", "Tendencia", List.of(trends.trend().message()));

        AnomaliesResponse anomalies = intelligence.anomalies(userId, days);
        List<String> anomalyLines = new ArrayList<>();
        anomalyLines.add(anomalies.message());
        anomalies.anomalies().stream().limit(8).forEach(a -> anomalyLines.add(DATE.format(a.measuredAt()) + " UTC - "
            + a.title() + ": " + a.message()));
        builder.section("anomalies", "Anomalías detectadas", anomalyLines);

        List<AlertResponse> alerts = alertService.list(userId, false, 5);
        builder.section("alerts", "Alertas recientes", alerts.stream()
            .map(a -> DATE.format(a.createdAt()) + " UTC - " + a.title() + ": " + a.message()).toList());

        ForecastResponse forecast = intelligence.forecast(userId, 7);
        builder.section("forecast", "Predicción", forecast.forecast().available()
            ? List.of(forecast.outlook(), "Método: " + forecast.forecast().method() + ".")
            : List.of("No disponible: " + forecast.forecast().reason()));

        RecommendationResponse recommendation = intelligence.recommendations(userId);
        List<String> recLines = new ArrayList<>();
        recLines.add(recommendation.advice().summary());
        recommendation.advice().actions().forEach(action -> recLines.add("- " + action));
        if (recommendation.advice().needsExpert()) {
            recLines.add("Evaluación adicional: " + recommendation.advice().expertNote());
        }
        builder.section("recommendations", "Recomendaciones", recLines);

        builder.section("conclusion", "Conclusión", List.of(conclusion(userId, days, includeAi)));
        auditService.record(userId, "REPORT_GENERATED", "Informe de " + days + " días");
        return builder.build();
    }

    private String conclusion(Long userId, int days, boolean includeAi) {
        if (!includeAi) {
            return intelligence.ruleBasedConclusion(userId, days) + " (generado con reglas y estadística)";
        }
        try {
            ConclusionResponse response = intelligence.conclusion(userId, days);
            return response.text() + " (" + response.sourceLabel() + ")";
        } catch (AiUnavailableException ex) {
            return intelligence.ruleBasedConclusion(userId, days) + " (IA no disponible: generado con reglas)";
        }
    }

    public String renderText(Report report) {
        StringBuilder text = new StringBuilder();
        text.append("AQUASENSE AI\n").append(report.title()).append('\n');
        text.append("Perfil: ").append(report.profileName()).append('\n');
        text.append("Periodo: ").append(DATE.format(report.periodStart())).append(" - ")
            .append(DATE.format(report.periodEnd())).append(" UTC\n");
        text.append("Generado: ").append(DATE.format(report.generatedAt())).append(" UTC\n\n");
        for (ReportSection section : report.sections()) {
            text.append(section.title().toUpperCase(Locale.ROOT)).append('\n');
            section.lines().forEach(line -> text.append(line).append('\n'));
            text.append('\n');
        }
        text.append(report.disclaimer()).append('\n');
        return text.toString();
    }
}
