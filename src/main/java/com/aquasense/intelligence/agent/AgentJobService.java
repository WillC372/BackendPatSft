package com.aquasense.intelligence.agent;

import com.aquasense.audit.AuditService;
import com.aquasense.common.BusinessException;
import com.aquasense.intelligence.IntelligenceFacade;
import com.aquasense.intelligence.ai.AiProviderProxy;
import com.aquasense.intelligence.ai.AiTextProvider;
import com.aquasense.knowledge.Citation;
import com.aquasense.knowledge.GuidelineRetriever;
import com.aquasense.report.Report;
import com.aquasense.report.ReportService;
import com.aquasense.report.ReportSection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Runs the agent in the background so the UI shows its real progress (it polls the job state).
 * It uses a bounded thread pool (Bulkhead): long investigations cannot exhaust the threads that serve the API.
 * If a provider fails, it restarts the investigation with the next one; if all fail, it builds the rule-based report.
 */
@Service
public class AgentJobService {

    private static final Logger LOG = LoggerFactory.getLogger(AgentJobService.class);
    private static final Duration JOB_TTL = Duration.ofMinutes(30);
    private static final String DISCLAIMER = "Informe elaborado a partir de mediciones de pH. No reemplaza un análisis de laboratorio "
        + "ni demuestra que el agua sea potable.";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final ReportAgent agent;
    private final AiProviderProxy aiProvider;
    private final IntelligenceFacade facade;
    private final ReportService reportService;
    private final AgentReportRepository repository;
    private final AuditService auditService;
    private final ObjectMapper mapper;
    private final GuidelineRetriever guidelines;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(8), runnable -> {
            Thread thread = new Thread(runnable, "aquasense-agent");
            thread.setDaemon(true);
            return thread;
        });

    public AgentJobService(ReportAgent agent, AiProviderProxy aiProvider, IntelligenceFacade facade, ReportService reportService,
                           AgentReportRepository repository, AuditService auditService, ObjectMapper mapper,
                           GuidelineRetriever guidelines) {
        this.agent = agent;
        this.aiProvider = aiProvider;
        this.facade = facade;
        this.reportService = reportService;
        this.repository = repository;
        this.auditService = auditService;
        this.mapper = mapper;
        this.guidelines = guidelines;
    }

    public AgentJobResponse start(Long userId, AgentReportRequest request) {
        jobs.values().removeIf(Job::isExpired);
        if (jobs.values().stream().anyMatch(j -> j.userId.equals(userId) && "RUNNING".equals(j.status))) {
            throw BusinessException.conflict("Ya hay un informe en elaboración. Espera a que termine.");
        }
        int maxDays = facade.maxAnalysisDays(userId);
        int days = Math.min(request.days() == null ? 7 : request.days(), maxDays);
        String focus = request.focus() == null || request.focus().isBlank()
            ? "Informe general del estado del agua en el periodo." : request.focus().trim();
        boolean useAi = aiProvider.chain().stream().anyMatch(p -> p.isAvailable() && p.supportsTools());
        if (useAi) {
            aiProvider.ensureQuota(userId);
        }
        Job job = new Job(UUID.randomUUID().toString(), userId);
        jobs.put(job.id, job);
        AgentContext context = new AgentContext(userId, days, maxDays, facade.maxForecastHorizon(userId));
        try {
            executor.execute(() -> runJob(job, context, focus, useAi));
        } catch (RejectedExecutionException ex) {
            jobs.remove(job.id);
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "El agente está ocupado. Intenta de nuevo en un minuto.");
        }
        return toResponse(job);
    }

    public AgentJobResponse status(Long userId, String jobId) {
        Job job = jobs.get(jobId);
        if (job == null || !job.userId.equals(userId)) {
            throw BusinessException.notFound("El trabajo no existe o ya expiró.");
        }
        return toResponse(job);
    }

    public List<AgentReportResponse.Summary> history(Long userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 30))).stream()
            .map(r -> new AgentReportResponse.Summary(r.getId(), r.getCreatedAt(), r.getSource(), r.getProvider(),
                r.getPeriodDays(), r.getTitle()))
            .toList();
    }

    public AgentReportResponse get(Long userId, Long id) {
        AgentReportRecord record = repository.findById(id).filter(r -> r.getUserId().equals(userId))
            .orElseThrow(() -> BusinessException.notFound("El informe no existe."));
        try {
            return mapper.readValue(record.getPayload(), AgentReportResponse.class).withId(record.getId());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored agent report " + id + " is corrupted", ex);
        }
    }

    public String renderText(AgentReportResponse r) {
        StringBuilder t = new StringBuilder("AQUASENSE AI\n").append(r.report().title()).append('\n');
        t.append("Elaborado: ").append(DATE.format(r.createdAt())).append(" · ").append(r.sourceLabel())
            .append(r.provider() == null ? "" : " (" + r.provider() + " · " + r.model() + ")").append('\n');
        t.append("Objetivo: ").append(r.focus()).append("\nPeriodo: últimos ").append(r.periodDays()).append(" días\n\n");
        t.append("RESUMEN\n").append(r.report().executiveSummary()).append("\n\n");
        if (!r.report().keyMetrics().isEmpty()) {
            t.append("CIFRAS CLAVE\n");
            r.report().keyMetrics().forEach(m -> t.append("- ").append(m.label()).append(": ").append(m.value()).append('\n'));
            t.append('\n');
        }
        r.report().sections().forEach(s -> t.append(s.heading().toUpperCase()).append('\n').append(s.body()).append("\n\n"));
        t.append("RECOMENDACIONES\n");
        r.report().recommendations().forEach(rec -> t.append(rec.priority()).append(". ").append(rec.action())
            .append(rec.rationale().isBlank() ? "" : " (" + rec.rationale() + ")").append('\n'));
        if (r.citations() != null && !r.citations().isEmpty()) {
            t.append("\nFUENTES NORMATIVAS (cita literal)\n");
            r.citations().forEach(c -> t.append("- ").append(c.organization()).append(", ").append(c.title()).append(", ")
                .append(c.section()).append(": \"").append(c.quote()).append("\" ").append(c.url()).append('\n'));
        }
        if (!r.report().limitations().isEmpty()) {
            t.append("\nLIMITACIONES\n");
            r.report().limitations().forEach(l -> t.append("- ").append(l).append('\n'));
        }
        if (r.verification() != null && r.verification().checked() > 0) {
            t.append("\nVerificación: ").append(r.verification().verified()).append(" de ").append(r.verification().checked())
                .append(" cifras coinciden con los datos.\n");
        }
        if (!r.trace().isEmpty()) {
            t.append("\nCÓMO SE ELABORÓ\n");
            r.trace().forEach(s -> t.append(s.step()).append(". ").append(s.label()).append(" → ").append(s.summary()).append('\n'));
        }
        return t.append('\n').append(r.disclaimer()).append('\n').toString();
    }

    private void runJob(Job job, AgentContext context, String focus, boolean useAi) {
        long start = System.currentTimeMillis();
        String note = null;
        AgentReportResponse response = null;
        try {
            if (useAi) {
                List<String> failures = new ArrayList<>();
                for (AiTextProvider provider : aiProvider.chain().stream().filter(p -> p.isAvailable() && p.supportsTools()).toList()) {
                    job.phase = "Investigando con " + provider.providerName() + " · " + provider.modelName();
                    long providerStart = System.currentTimeMillis();
                    try {
                        ReportAgent.Result result = agent.run(provider, context, focus, job.trace::add, job.trace.size());
                        aiProvider.recordUsage(context.userId(), "AGENT_REPORT", result.provider(), result.model(), result.inputTokens(),
                            result.outputTokens(), System.currentTimeMillis() - providerStart);
                        response = new AgentReportResponse(null, Instant.now(), "AI", "Elaborado por el agente de IA", result.provider(),
                            result.model(), context.periodDays(), focus, result.report(), result.verification(), List.copyOf(job.trace),
                            result.toolCalls(), System.currentTimeMillis() - start,
                            failures.isEmpty() ? null : "Proveedores que fallaron antes: " + String.join(" | ", failures), DISCLAIMER,
                            guidelines.resolve(result.report().citedSourceIds()), result.discardedCitations());
                        break;
                    } catch (RuntimeException ex) {
                        if (!ReportAgent.isRecoverable(ex)) {
                            throw ex;
                        }
                        failures.add(ex.getMessage());
                        aiProvider.recordFailure(context.userId(), "AGENT_REPORT", provider.providerName(), provider.modelName(),
                            System.currentTimeMillis() - providerStart, ex.getMessage());
                        job.trace.add(new TraceStep(job.trace.size() + 1, "provider", provider.providerName() + " no pudo terminar",
                            Map.of("model", provider.modelName()), ex.getMessage(), 0, "ERROR"));
                    }
                }
                if (response == null) {
                    note = "La IA no pudo elaborar el informe (" + String.join(" | ", failures) + "). Se generó el informe por reglas.";
                }
            } else {
                note = "No hay un proveedor de IA con herramientas configurado; se generó el informe por reglas.";
            }
            if (response == null) {
                job.phase = "Generando informe por reglas";
                response = ruleBased(context, focus, note, List.copyOf(job.trace), System.currentTimeMillis() - start);
            }
            AgentReportRecord saved = repository.save(new AgentReportRecord(context.userId(), response.source(), response.provider(),
                response.model(), response.periodDays(), response.report().title(), mapper.writeValueAsString(response), Instant.now()));
            response = response.withId(saved.getId());
            auditService.recordIndependently(context.userId(), "AGENT_REPORT", response.source() + " " + response.periodDays()
                + " días, " + response.toolCalls() + " consultas");
            job.result = response;
            job.status = "DONE";
            job.phase = "Informe listo";
        } catch (Exception ex) {
            LOG.error("Agent job {} failed", job.id, ex);
            job.error = ex instanceof BusinessException ? ex.getMessage() : "No se pudo elaborar el informe. Intenta de nuevo.";
            job.status = "FAILED";
            job.phase = "Error";
        } finally {
            job.finishedAt = Instant.now();
        }
    }

    private AgentReportResponse ruleBased(AgentContext context, String focus, String note, List<TraceStep> trace, long elapsed) {
        Report report = reportService.build(context.userId(), context.periodDays(), false);
        List<AgentReport.Section> sections = new ArrayList<>();
        List<AgentReport.Recommendation> recommendations = new ArrayList<>();
        String summary = "Informe generado con reglas y estadística.";
        for (ReportSection s : report.sections()) {
            if ("recommendations".equals(s.key())) {
                s.lines().stream().filter(l -> l.startsWith("- ")).limit(6)
                    .forEach(l -> recommendations.add(new AgentReport.Recommendation(l.substring(2), 2, "")));
            }
            if ("conclusion".equals(s.key()) && !s.lines().isEmpty()) {
                summary = s.lines().get(0);
            }
            AgentReport.ChartKind chart = switch (s.key()) {
                case "summary" -> AgentReport.ChartKind.PH_EVOLUTION;
                case "anomalies" -> AgentReport.ChartKind.ANOMALIES;
                case "forecast" -> AgentReport.ChartKind.FORECAST;
                default -> AgentReport.ChartKind.NONE;
            };
            sections.add(new AgentReport.Section(s.title(), String.join("\n", s.lines()), chart));
        }
        if (sections.size() < 2) {
            sections.add(new AgentReport.Section("Alcance", "Periodo de " + context.periodDays() + " días.", AgentReport.ChartKind.NONE));
        }
        if (recommendations.isEmpty()) {
            recommendations.add(new AgentReport.Recommendation("Mantener la frecuencia de medición.", 3, ""));
        }
        List<Citation> sources = facade.recommendations(context.userId()).sources();
        AgentReport agentReport = new AgentReport(report.title(), summary, List.of(
            new AgentReport.Metric("Mediciones", String.valueOf(report.measurementCount())),
            new AgentReport.Metric("Perfil", report.profileName())), sections, recommendations,
            report.containsSimulatedData() ? List.of("El periodo incluye lecturas del simulador.") : List.of(),
            sources.stream().map(Citation::id).toList());
        return new AgentReportResponse(null, Instant.now(), "RULES", "Generado con reglas y estadística", null, null,
            context.periodDays(), focus, agentReport, null, trace, 0, elapsed, note, DISCLAIMER, sources, List.of());
    }

    private AgentJobResponse toResponse(Job job) {
        long elapsed = Duration.between(job.startedAt, job.finishedAt == null ? Instant.now() : job.finishedAt).toMillis();
        return new AgentJobResponse(job.id, job.status, job.phase, List.copyOf(job.trace), job.result, job.error, elapsed);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static final class Job {
        final String id;
        final Long userId;
        final Instant startedAt = Instant.now();
        final List<TraceStep> trace = new CopyOnWriteArrayList<>();
        volatile String status = "RUNNING";
        volatile String phase = "Preparando la investigación";
        volatile AgentReportResponse result;
        volatile String error;
        volatile Instant finishedAt;

        Job(String id, Long userId) {
            this.id = id;
            this.userId = userId;
        }

        boolean isExpired() {
            return finishedAt != null && Instant.now().isAfter(finishedAt.plus(JOB_TTL));
        }
    }
}
