package com.aquasense.report.pdf;

import com.aquasense.intelligence.agent.AgentJobService;
import com.aquasense.intelligence.agent.AgentReport;
import com.aquasense.intelligence.agent.AgentReportResponse;
import com.aquasense.intelligence.analysis.Anomaly;
import com.aquasense.intelligence.analysis.AnomalyDetector;
import com.aquasense.intelligence.analysis.Forecast;
import com.aquasense.intelligence.analysis.Forecaster;
import com.aquasense.knowledge.Citation;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementService;
import com.aquasense.profile.PhStatus;
import com.aquasense.profile.ProfileService;
import com.aquasense.profile.WaterProfile;
import com.aquasense.report.Report;
import com.aquasense.report.ReportSection;
import com.aquasense.report.ReportService;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Informes en PDF: el del agente de IA y el informe por reglas. Usa los mismos datos que la versión en pantalla
 * y dibuja las gráficas que eligió el agente (evolución, anomalías, predicción o distribución).
 */
@Service
public class ReportPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm",
        Locale.forLanguageTag("es-CO")).withZone(ZoneId.of("America/Bogota"));
    private static final String FOOTER = "AquaSense AI · Informe orientativo basado en el pH; no reemplaza un análisis de laboratorio.";

    private final AgentJobService agentJobs;
    private final ReportService reportService;
    private final MeasurementService measurementService;
    private final ProfileService profileService;
    private final AnomalyDetector anomalyDetector;
    private final Forecaster forecaster;

    public ReportPdfService(AgentJobService agentJobs, ReportService reportService, MeasurementService measurementService,
                            ProfileService profileService, AnomalyDetector anomalyDetector, Forecaster forecaster) {
        this.agentJobs = agentJobs;
        this.reportService = reportService;
        this.measurementService = measurementService;
        this.profileService = profileService;
        this.anomalyDetector = anomalyDetector;
        this.forecaster = forecaster;
    }

    @Transactional
    public byte[] agentReport(Long userId, Long reportId) {
        AgentReportResponse r = agentJobs.get(userId, reportId);
        AgentReport rep = r.report();
        ChartData data = chartData(userId, r.createdAt(), r.periodDays());
        try (PdfWriter pdf = new PdfWriter(rep.title(), FOOTER)) {
            pdf.banner("Informe del agente de IA · últimos " + r.periodDays() + " días", rep.title(),
                "Elaborado el " + DATE.format(r.createdAt()) + " · " + r.sourceLabel()
                    + (r.provider() == null ? "" : " (" + r.provider() + " · " + r.model() + ")") + " · Perfil: " + data.profile().getName());
            pdf.paragraph("Objetivo: " + r.focus(), 10, PdfWriter.MUTED);
            pdf.heading("Resumen ejecutivo").paragraph(rep.executiveSummary());
            if (!rep.keyMetrics().isEmpty()) {
                pdf.metrics(rep.keyMetrics().stream().map(m -> new String[] {m.label(), m.value()}).toList());
            }
            if (r.verification() != null && r.verification().checked() > 0) {
                boolean all = r.verification().unverified().isEmpty();
                pdf.callout(all ? "Cifras verificadas" : "Cifras por revisar", List.of(
                    r.verification().verified() + " de " + r.verification().checked()
                        + " cifras de pH del informe coinciden con los datos que consultó el agente."
                        + (all ? "" : " No coinciden: " + String.join(", ", r.verification().unverified()) + ".")),
                    all ? PdfWriter.OK : PdfWriter.WARN);
            }
            for (AgentReport.Section s : rep.sections()) {
                pdf.heading(s.heading()).paragraph(s.body());
                BufferedImage chart = chart(s.chart(), data);
                if (chart != null) {
                    pdf.image(chart, caption(s.chart(), data));
                }
            }
            pdf.heading("Recomendaciones");
            pdf.list(rep.recommendations().stream().map(a -> a.action() + (a.rationale().isBlank() ? "" : " — " + a.rationale())).toList(),
                rep.recommendations().stream().map(a -> "P" + a.priority()).toList(),
                rep.recommendations().stream().map(a -> a.priority() == 1 ? PdfWriter.CRIT : a.priority() == 2 ? PdfWriter.WARN : PdfWriter.OK).toList());
            if (!rep.limitations().isEmpty()) {
                pdf.callout("Limitaciones", rep.limitations(), PdfWriter.MUTED);
            }
            citations(pdf, r.citations());
            if (r.trace() != null && !r.trace().isEmpty()) {
                pdf.heading("Cómo se elaboró");
                pdf.list(r.trace().stream().map(t -> traceLine(t.label(), t.summary())).toList(), null, null);
            }
            pdf.paragraph(r.disclaimer(), 8.5f, PdfWriter.MUTED);
            return pdf.finish();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Transactional
    public byte[] ruleReport(Long userId, int days, boolean includeAi) {
        Report report = reportService.build(userId, days, includeAi);
        long period = Math.max(1, Duration.between(report.periodStart(), report.periodEnd()).toDays());
        ChartData data = chartData(userId, report.periodEnd(), (int) period);
        try (PdfWriter pdf = new PdfWriter(report.title(), FOOTER)) {
            pdf.banner("Informe de calidad del agua", report.title(),
                "Generado el " + DATE.format(report.generatedAt()) + " · Del " + DATE.format(report.periodStart()) + " al "
                    + DATE.format(report.periodEnd()) + " · Perfil: " + report.profileName());
            pdf.metrics(List.of(new String[] {"Mediciones", String.valueOf(report.measurementCount())},
                new String[] {"Periodo", period + " días"}, new String[] {"Perfil", report.profileName()},
                new String[] {"Rango ideal", data.profile().rangeLabel()}));
            if (report.containsSimulatedData()) {
                pdf.callout("Datos simulados", List.of("Parte de las mediciones del periodo proviene del simulador y no representa agua real."),
                    PdfWriter.WARN);
            }
            for (ReportSection s : report.sections()) {
                pdf.heading(s.title());
                List<String> bullets = s.lines().stream().filter(l -> l.startsWith("- ")).map(l -> l.substring(2)).toList();
                List<String> text = s.lines().stream().filter(l -> !l.startsWith("- ")).toList();
                if (!text.isEmpty()) {
                    pdf.paragraph(String.join("\n", text));
                }
                if (!bullets.isEmpty()) {
                    pdf.list(bullets, null, null);
                }
                AgentReport.ChartKind kind = switch (s.key()) {
                    case "summary" -> AgentReport.ChartKind.PH_EVOLUTION;
                    case "anomalies" -> AgentReport.ChartKind.ANOMALIES;
                    case "forecast" -> AgentReport.ChartKind.FORECAST;
                    default -> AgentReport.ChartKind.NONE;
                };
                BufferedImage chart = chart(kind, data);
                if (chart != null) {
                    pdf.image(chart, caption(kind, data));
                }
                if ("summary".equals(s.key())) {
                    BufferedImage bars = chart(AgentReport.ChartKind.DISTRIBUTION, data);
                    if (bars != null) {
                        pdf.image(bars, caption(AgentReport.ChartKind.DISTRIBUTION, data));
                    }
                }
            }
            pdf.paragraph(report.disclaimer(), 8.5f, PdfWriter.MUTED);
            return pdf.finish();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void citations(PdfWriter pdf, List<Citation> citations) throws IOException {
        if (citations == null || citations.isEmpty()) {
            return;
        }
        pdf.heading("Fuentes normativas citadas");
        int n = 1;
        for (Citation c : citations) {
            pdf.citation(n++, c.organization() + " · " + c.section(), c.quote(), c.summaryEs(), c.url());
        }
    }

    private ChartData chartData(Long userId, Instant end, int days) {
        WaterProfile profile = profileService.activeProfile(userId);
        List<Measurement> measurements = measurementService.between(userId, end.minus(Duration.ofDays(days)), end);
        return new ChartData(profile, measurements, anomalyDetector.scan(measurements, profile),
            forecaster.forecast(measurements, 3, end));
    }

    private static BufferedImage chart(AgentReport.ChartKind kind, ChartData d) {
        if (kind == null || kind == AgentReport.ChartKind.NONE || d.measurements().size() < 2) {
            return null;
        }
        List<ChartPainter.Point> points = d.measurements().stream().map(m -> new ChartPainter.Point(m.getMeasuredAt(), m.getPh())).toList();
        WaterProfile p = d.profile();
        return switch (kind) {
            case PH_EVOLUTION -> ChartPainter.line(points, p.getIdealMin(), p.getIdealMax(), List.of(), List.of());
            case ANOMALIES -> ChartPainter.line(points, p.getIdealMin(), p.getIdealMax(),
                d.anomalies().stream().map(a -> new ChartPainter.Point(a.measuredAt(), a.ph())).toList(), List.of());
            case FORECAST -> ChartPainter.line(points, p.getIdealMin(), p.getIdealMax(), List.of(), d.forecast().available()
                ? d.forecast().points().stream().map(f -> new ChartPainter.Band(f.at(), f.predicted(), f.lower(), f.upper())).toList()
                : List.of());
            case DISTRIBUTION -> {
                List<Integer> counts = new ArrayList<>();
                for (PhStatus s : PhStatus.values()) {
                    counts.add((int) d.measurements().stream().filter(m -> m.getStatus() == s).count());
                }
                yield ChartPainter.bars(List.of("En rango", "Precaución", "Crítico"), counts,
                    List.of(PdfWriter.OK, new Color(0xe0, 0x9a, 0x2a), PdfWriter.CRIT));
            }
            default -> null;
        };
    }

    private static String caption(AgentReport.ChartKind kind, ChartData d) {
        String range = "Franja verde: rango ideal " + d.profile().rangeLabel() + ". " + d.measurements().size() + " mediciones.";
        return switch (kind) {
            case PH_EVOLUTION -> "Evolución del pH. " + range;
            case ANOMALIES -> "Evolución del pH con " + d.anomalies().size() + " anomalías marcadas en rojo. " + range;
            case FORECAST -> d.forecast().available()
                ? "Predicción a " + d.forecast().horizonDays() + " días (línea discontinua) con intervalo del 95 %. " + range
                : "Predicción no disponible: " + d.forecast().reason();
            case DISTRIBUTION -> "Mediciones del periodo por estado según el perfil " + d.profile().getName() + ".";
            default -> null;
        };
    }

    private record ChartData(WaterProfile profile, List<Measurement> measurements, List<Anomaly> anomalies, Forecast forecast) {
    }

    /** "Google Gemini no pudo terminar: Google Gemini: límite..." queda sin el nombre repetido. */
    public static String traceLine(String label, String summary) {
        if (summary == null || summary.isBlank()) {
            return label;
        }
        int colon = summary.indexOf(": ");
        if (colon > 0 && label.startsWith(summary.substring(0, colon))) {
            summary = summary.substring(colon + 2);
        }
        return label + ": " + summary;
    }
}
