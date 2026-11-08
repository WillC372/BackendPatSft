package com.aquasense.intelligence.diagnosis;

import com.aquasense.audit.AuditService;
import com.aquasense.common.BusinessException;
import com.aquasense.intelligence.advice.Advice;
import com.aquasense.intelligence.advice.AdviceStrategyResolver;
import com.aquasense.intelligence.ai.AiAnswer;
import com.aquasense.intelligence.ai.AiPrompt;
import com.aquasense.intelligence.ai.AiProviderProxy;
import com.aquasense.intelligence.ai.AiUnavailableException;
import com.aquasense.intelligence.analysis.AnomalyDetector;
import com.aquasense.intelligence.analysis.Forecast;
import com.aquasense.intelligence.analysis.Forecaster;
import com.aquasense.intelligence.analysis.TrendAnalyzer;
import com.aquasense.intelligence.analysis.TrendResult;
import com.aquasense.knowledge.GuidelineRetriever;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementService;
import com.aquasense.measurement.MeasurementSource;
import com.aquasense.profile.Classification;
import com.aquasense.profile.PhClassifier;
import com.aquasense.profile.ProfileService;
import com.aquasense.profile.WaterProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Smart diagnosis: 1) the system computes the facts (statistics, anomalies, trend, forecast, rules);
 * 2) the AI (Gemini, Groq...) reasons on them and answers with structured JSON; 3) the validator reviews the answer
 * and applies safeguards; 4) if something fails, the rule-based diagnosis with the same structure is used. The result is stored.
 */
@Service
public class DiagnosisService {

    static final String SYSTEM_PROMPT = """
        You are the diagnosis engine of AquaSense AI, a system that monitors the pH of water.
        You receive facts already computed by the system (measurements, statistics, anomalies, trend, forecast and profile rules).
        Your task is to produce a technical diagnosis in the JSON format given. Write every text field in Spanish. Rules:
        - Use only the provided facts. Do not invent measurements, dates, values or sensors that do not exist.
        - Every finding must cite in "evidence" concrete values or dates from the facts, with the date format "2026-11-04 19:52 UTC".
        - riskLevel: LOW if everything is in range and stable; MODERATE if there is caution, a trend moving away from the range or mild anomalies;
          HIGH if there are out-of-range values or a clear trend toward the critical state; CRITICAL if the last measurement is critical.
        - probableCauses: plausible causes for the water type of the profile, with their likelihood and the evidence that suggests them.
        - actions: concrete and safe actions ordered by priority (1 = first) with a timeframe ("Hoy", "En 24 h", "Esta semana").
          Do not give exact chemical doses; suggest small doses, following the manufacturer and measuring again.
        - dataGaps: what information is missing for a more reliable diagnosis (more measurements, temperature, alkalinity, chlorine, etc.).
        - confidence between 0 and 1: low if there are few measurements or simulated data.
        - nextMeasurementHours: when to measure again according to the risk.
        - pH alone does not prove that water is drinkable; do not claim it is drinkable.
        - If there are simulated measurements, say so in a finding: they do not represent real water.
        - guidelineFragments contains literal quotes of official guidelines that apply to the profile. If a finding or an action
          relies on one, mention the organization in the text (for example "según la CDC") and include its id in citedSourceIds.
          Only cite ids from that list and do not attribute to a guideline anything its fragment does not say.
          If there are no fragments, leave citedSourceIds empty and do not mention guidelines.""";

    private static final int DIAGNOSIS_SOURCES = 4;

    private static final java.time.format.DateTimeFormatter DATE =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(java.time.ZoneOffset.UTC);

    private static final String DISCLAIMER =
        "Diagnóstico orientativo basado en el pH. No reemplaza un análisis de laboratorio ni demuestra potabilidad.";

    private final MeasurementService measurementService;
    private final ProfileService profileService;
    private final PhClassifier classifier;
    private final AnomalyDetector anomalyDetector;
    private final TrendAnalyzer trendAnalyzer;
    private final Forecaster forecaster;
    private final AdviceStrategyResolver adviceResolver;
    private final AiProviderProxy aiProvider;
    private final DiagnosisValidator validator;
    private final RuleBasedDiagnoser ruleBased;
    private final DiagnosisRepository repository;
    private final ObjectMapper mapper;
    private final AuditService auditService;
    private final GuidelineRetriever guidelines;
    private final com.aquasense.intelligence.deep.DeepForecastService deepForecast;

    public DiagnosisService(MeasurementService measurementService, ProfileService profileService, PhClassifier classifier,
                            AnomalyDetector anomalyDetector, TrendAnalyzer trendAnalyzer, Forecaster forecaster,
                            AdviceStrategyResolver adviceResolver, AiProviderProxy aiProvider, DiagnosisValidator validator,
                            RuleBasedDiagnoser ruleBased, DiagnosisRepository repository, ObjectMapper mapper,
                            AuditService auditService, GuidelineRetriever guidelines,
                            com.aquasense.intelligence.deep.DeepForecastService deepForecast) {
        this.measurementService = measurementService;
        this.profileService = profileService;
        this.classifier = classifier;
        this.anomalyDetector = anomalyDetector;
        this.trendAnalyzer = trendAnalyzer;
        this.forecaster = forecaster;
        this.adviceResolver = adviceResolver;
        this.aiProvider = aiProvider;
        this.validator = validator;
        this.ruleBased = ruleBased;
        this.repository = repository;
        this.mapper = mapper;
        this.auditService = auditService;
        this.guidelines = guidelines;
        this.deepForecast = deepForecast;
    }

    public DiagnosisResponse generate(Long userId, int days) {
        DiagnosisFacts facts = facts(userId, days);
        Diagnosis diagnosis;
        String source = "RULES";
        String provider = null;
        String model = null;
        String note;
        List<String> adjustments = List.of();
        if (!facts.hasData()) {
            diagnosis = ruleBased.diagnose(facts);
            note = "Sin mediciones en el periodo: no se consultó a la IA.";
        } else if (!aiProvider.isAvailable()) {
            diagnosis = ruleBased.diagnose(facts);
            note = "La IA generativa no está configurada; se muestra el diagnóstico por reglas.";
        } else {
            try {
                AiAnswer answer = aiProvider.complete(new AiPrompt(userId, "DIAGNOSIS", SYSTEM_PROMPT, factsJson(facts),
                    DiagnosisSchema.NAME, DiagnosisSchema.forSources(facts.guidelineIds())));
                DiagnosisValidator.Result result = validator.validate(answer.text(), facts);
                diagnosis = result.diagnosis();
                adjustments = result.adjustments();
                source = "AI";
                provider = answer.provider();
                model = answer.model();
                note = answer.cached() ? "Respuesta reutilizada de la caché (no consumió cuota)." : null;
            } catch (AiUnavailableException ex) {
                diagnosis = ruleBased.diagnose(facts);
                note = "IA no disponible en este momento; se muestra el diagnóstico por reglas. Detalle: " + ex.getMessage();
            } catch (DiagnosisValidator.InvalidDiagnosisException ex) {
                diagnosis = ruleBased.diagnose(facts);
                note = "La respuesta de la IA no superó la validación (" + ex.getMessage() + "); se muestra el diagnóstico por reglas.";
            } catch (BusinessException ex) {
                if (ex.getStatus() != HttpStatus.TOO_MANY_REQUESTS) {
                    throw ex;
                }
                diagnosis = ruleBased.diagnose(facts);
                note = ex.getMessage();
            }
        }
        Instant now = Instant.now();
        DiagnosisResponse response = new DiagnosisResponse(null, now, source,
            "AI".equals(source) ? "Generado por IA y validado por el sistema" : "Generado con reglas y estadística",
            provider, model, diagnosis.riskLevel(), diagnosis.riskLevel().label(), diagnosis.summary(), diagnosis.findings(),
            diagnosis.probableCauses(), diagnosis.actions(), diagnosis.dataGaps(), diagnosis.confidence(),
            diagnosis.nextMeasurementHours(), facts.count(), facts.periodDays(), facts.profile().getName(),
            facts.includesSimulated(), adjustments, note, DISCLAIMER, guidelines.resolve(diagnosis.citedSourceIds()),
            facts.guidelines().isEmpty() ? guidelines.coverage(facts.profile().getType()) : null);
        DiagnosisRecord saved = repository.save(new DiagnosisRecord(userId, source, provider, model,
            diagnosis.riskLevel().name(), toJson(response), now));
        auditService.recordIndependently(userId, "AI_DIAGNOSIS", source + (provider == null ? "" : " " + provider + " " + model)
            + " riesgo " + diagnosis.riskLevel());
        return response.withId(saved.getId());
    }

    public Optional<DiagnosisResponse> latest(Long userId) {
        return repository.findTopByUserIdOrderByCreatedAtDesc(userId).map(this::fromRecord);
    }

    public List<DiagnosisResponse> history(Long userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 50)))
            .stream().map(this::fromRecord).toList();
    }

    DiagnosisFacts facts(Long userId, int days) {
        WaterProfile profile = profileService.activeProfile(userId);
        Instant now = Instant.now();
        List<Measurement> current = measurementService.between(userId, now.minus(Duration.ofDays(days)), now);
        List<Measurement> previous = measurementService.between(userId, now.minus(Duration.ofDays(2L * days)),
            now.minus(Duration.ofDays(days)));
        Classification latest = current.isEmpty() ? null : classifier.classify(current.get(current.size() - 1).getPh(), profile);
        TrendResult trend = trendAnalyzer.analyze(current, previous);
        Forecast forecast = forecaster.forecast(current, 3, now);
        Advice advice = latest == null ? new Advice("", List.of(), List.of(), false, null)
            : adviceResolver.forType(profile.getType()).advise(latest, profile);
        return new DiagnosisFacts(profile, days, current, latest, trend, anomalyDetector.scan(current, profile), forecast,
            advice, count(current, MeasurementSource.SIMULATED), count(current, MeasurementSource.REAL),
            count(current, MeasurementSource.MANUAL), guidelines.forReading(profile, latest, DIAGNOSIS_SOURCES));
    }

    String factsJson(DiagnosisFacts facts) {
        Map<String, Object> root = new LinkedHashMap<>();
        WaterProfile p = facts.profile();
        root.put("profile", Map.of("name", p.getName(), "type", p.getType().label(),
            "idealRange", List.of(p.getIdealMin(), p.getIdealMax()),
            "cautionRange", List.of(p.getCautionMin(), p.getCautionMax()),
            "description", p.getDescription()));
        root.put("periodDays", facts.periodDays());
        root.put("totalMeasurements", facts.count());
        root.put("origin", Map.of("meter", facts.realCount(), "manual", facts.manualCount(), "simulator", facts.simulatedCount(),
            "aiPhoto", count(facts.measurements(), MeasurementSource.PHOTO)));
        List<Measurement> recent = facts.measurements().subList(Math.max(0, facts.count() - 15), facts.count());
        root.put("lastMeasurements", recent.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dateUtc", DATE.format(m.getMeasuredAt()));
            row.put("ph", m.getPh());
            row.put("status", m.getStatus().label());
            row.put("origin", m.getSource().name());
            if (m.getTemperatureC() != null) {
                row.put("temperatureC", m.getTemperatureC());
            }
            return row;
        }).toList());
        if (facts.latest() != null) {
            root.put("lastClassification", Map.of("ph", facts.latest().ph(), "nature", facts.latest().nature().label(),
                "status", facts.latest().status().label(), "explanation", facts.latest().explanation()));
        }
        TrendResult t = facts.trend();
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("enoughData", t.enoughData());
        trend.put("message", t.message());
        if (t.enoughData()) {
            trend.put("mean", t.mean());
            trend.put("min", t.min());
            trend.put("max", t.max());
            trend.put("stdDev", t.stdDev());
            trend.put("phSlopePerDay", t.slopePerDay());
            trend.put("direction", t.directionLabel());
            trend.put("changeVsPreviousPeriod", t.changeVsPrevious());
        }
        root.put("trend", trend);
        root.put("anomalies", facts.anomalies().stream().limit(8).map(a -> Map.of("type", a.title(),
            "severity", a.severity().name(), "dateUtc", DATE.format(a.measuredAt()), "ph", a.ph(), "detail", a.message())).toList());
        Forecast f = facts.forecast();
        root.put("forecast", f.available()
            ? Map.of("available", true, "horizonDays", f.horizonDays(), "method", f.method(),
                "points", f.points().stream().map(pt -> Map.of("dateUtc", DATE.format(pt.at()), "ph", pt.predicted(),
                    "min95", pt.lower(), "max95", pt.upper())).toList())
            : Map.of("available", false, "reason", f.reason()));
        root.put("profileRules", Map.of("summary", facts.baseAdvice().summary(),
            "knownCauses", facts.baseAdvice().possibleCauses(), "recommendedActions", facts.baseAdvice().actions()));
        if (facts.guidelines().isEmpty()) {
            root.put("guidelineCoverage", guidelines.coverage(p.getType()));
        }
        try {
            if (facts.measurements().isEmpty()) {
                throw new IllegalStateException("no measurements");
            }
            var neural = deepForecast.forecast(facts.measurements().get(0).getUserId(), false);
            if (neural.trained() && neural.forecast() != null) {
                root.put("neuralNetworkForecast", Map.of("modelUsed", neural.evaluation().chosenLabel(),
                    "improvementVsBaselinePct", neural.evaluation().skillPercent(),
                    "forecast12h", neural.forecast().points().get(neural.forecast().points().size() - 1).chosen(),
                    "alert", neural.alert() == null ? "" : neural.alert().message()));
            }
        } catch (RuntimeException ex) {
            root.put("neuralNetworkForecast", Map.of("available", false));
        }
        root.put("guidelineFragments", facts.guidelines().stream().map(c -> Map.of("id", c.id(),
            "organization", c.organization(), "section", c.section(), "literalQuote", c.quote(), "summary", c.summaryEs())).toList());
        try {
            return "Facts computed by AquaSense (JSON):\n" + mapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private DiagnosisResponse fromRecord(DiagnosisRecord record) {
        try {
            return mapper.readValue(record.getPayload(), DiagnosisResponse.class).withId(record.getId());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored diagnosis " + record.getId() + " is corrupted", ex);
        }
    }

    private String toJson(DiagnosisResponse response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static long count(List<Measurement> measurements, MeasurementSource source) {
        return measurements.stream().filter(m -> m.getSource() == source).count();
    }
}
