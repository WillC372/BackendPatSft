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
 * Diagnóstico inteligente: 1) el sistema calcula los hechos (estadística, anomalías, tendencia, predicción, reglas);
 * 2) la IA (Gemini, Groq...) razona sobre ellos y responde con JSON estructurado; 3) el validador revisa la respuesta
 * y aplica salvaguardas; 4) si algo falla, se usa el diagnóstico por reglas con la misma estructura. Se guarda el resultado.
 */
@Service
public class DiagnosisService {

    static final String SYSTEM_PROMPT = """
        Eres el motor de diagnóstico de AquaSense AI, un sistema que monitorea el pH del agua.
        Recibes hechos ya calculados por el sistema (mediciones, estadística, anomalías, tendencia, predicción y reglas del perfil).
        Tu tarea es producir un diagnóstico técnico en español, en el formato JSON indicado. Reglas:
        - Usa solo los hechos entregados. No inventes mediciones, fechas, valores ni sensores que no existan.
        - Cada hallazgo debe citar en "evidence" valores o fechas concretas de los hechos, con el formato de fecha "2026-11-04 19:52 UTC".
        - riskLevel: LOW si todo está en rango y estable; MODERATE si hay precaución, tendencia que se aleja del rango o anomalías leves;
          HIGH si hay valores fuera de rango o una tendencia clara hacia el estado crítico; CRITICAL si la última medición es crítica.
        - probableCauses: causas plausibles para el tipo de agua del perfil, con su probabilidad y la evidencia que las sugiere.
        - actions: acciones concretas y seguras ordenadas por prioridad (1 = primero) con un plazo ("Hoy", "En 24 h", "Esta semana").
          No indiques dosis químicas exactas; indica dosis pequeñas, seguir al fabricante y volver a medir.
        - dataGaps: qué información falta para un diagnóstico más confiable (más mediciones, temperatura, alcalinidad, cloro, etc.).
        - confidence entre 0 y 1: baja si hay pocas mediciones o datos simulados.
        - nextMeasurementHours: cuándo volver a medir según el riesgo.
        - El pH por sí solo no demuestra que el agua sea potable; no afirmes potabilidad.
        - Si hay mediciones simuladas, dilo en un hallazgo: no representan agua real.
        - fragmentosNormativos contiene citas literales de normas oficiales aplicables al perfil. Si un hallazgo o una acción
          se apoya en uno, menciona la organización en el texto (por ejemplo "según la CDC") e incluye su id en citedSourceIds.
          Cita solo ids de esa lista y no atribuyas a una norma algo que su fragmento no dice.
          Si no hay fragmentos, deja citedSourceIds vacío y no menciones normas.""";

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
        root.put("perfil", Map.of("nombre", p.getName(), "tipo", p.getType().label(),
            "rangoIdeal", List.of(p.getIdealMin(), p.getIdealMax()),
            "rangoPrecaucion", List.of(p.getCautionMin(), p.getCautionMax()),
            "descripción", p.getDescription()));
        root.put("periodoDias", facts.periodDays());
        root.put("totalMediciones", facts.count());
        root.put("origen", Map.of("medidor", facts.realCount(), "manual", facts.manualCount(), "simulador", facts.simulatedCount(),
            "fotoConIa", count(facts.measurements(), MeasurementSource.PHOTO)));
        List<Measurement> recent = facts.measurements().subList(Math.max(0, facts.count() - 15), facts.count());
        root.put("ultimasMediciones", recent.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fechaUtc", DATE.format(m.getMeasuredAt()));
            row.put("ph", m.getPh());
            row.put("estado", m.getStatus().label());
            row.put("origen", m.getSource().name());
            if (m.getTemperatureC() != null) {
                row.put("temperaturaC", m.getTemperatureC());
            }
            return row;
        }).toList());
        if (facts.latest() != null) {
            root.put("clasificacionUltima", Map.of("ph", facts.latest().ph(), "naturaleza", facts.latest().nature().label(),
                "estado", facts.latest().status().label(), "explicacion", facts.latest().explanation()));
        }
        TrendResult t = facts.trend();
        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("suficientesDatos", t.enoughData());
        trend.put("mensaje", t.message());
        if (t.enoughData()) {
            trend.put("promedio", t.mean());
            trend.put("mínimo", t.min());
            trend.put("máximo", t.max());
            trend.put("desviacion", t.stdDev());
            trend.put("pendientePhPorDia", t.slopePerDay());
            trend.put("dirección", t.directionLabel());
            trend.put("cambioVsPeriodoAnterior", t.changeVsPrevious());
        }
        root.put("tendencia", trend);
        root.put("anomalías", facts.anomalies().stream().limit(8).map(a -> Map.of("tipo", a.title(),
            "severidad", a.severity().name(), "fechaUtc", DATE.format(a.measuredAt()), "ph", a.ph(), "detalle", a.message())).toList());
        Forecast f = facts.forecast();
        root.put("predicción", f.available()
            ? Map.of("disponible", true, "horizonteDias", f.horizonDays(), "método", f.method(),
                "puntos", f.points().stream().map(pt -> Map.of("fechaUtc", DATE.format(pt.at()), "ph", pt.predicted(),
                    "minimo95", pt.lower(), "maximo95", pt.upper())).toList())
            : Map.of("disponible", false, "motivo", f.reason()));
        root.put("reglasDelPerfil", Map.of("resumen", facts.baseAdvice().summary(),
            "causasConocidas", facts.baseAdvice().possibleCauses(), "accionesRecomendadas", facts.baseAdvice().actions()));
        if (facts.guidelines().isEmpty()) {
            root.put("coberturaNormativa", guidelines.coverage(p.getType()));
        }
        try {
            if (facts.measurements().isEmpty()) {
                throw new IllegalStateException("sin mediciones");
            }
            var neural = deepForecast.forecast(facts.measurements().get(0).getUserId(), false);
            if (neural.trained() && neural.forecast() != null) {
                root.put("prediccionRedNeuronal", Map.of("modeloUsado", neural.evaluation().chosenLabel(),
                    "mejoraFrenteReferenciaPct", neural.evaluation().skillPercent(),
                    "prediccion12h", neural.forecast().points().get(neural.forecast().points().size() - 1).chosen(),
                    "alerta", neural.alert() == null ? "" : neural.alert().message()));
            }
        } catch (RuntimeException ex) {
            root.put("prediccionRedNeuronal", Map.of("disponible", false));
        }
        root.put("fragmentosNormativos", facts.guidelines().stream().map(c -> Map.of("id", c.id(),
            "organizacion", c.organization(), "seccion", c.section(), "citaLiteral", c.quote(), "resumen", c.summaryEs())).toList());
        try {
            return "Hechos calculados por AquaSense (JSON):\n" + mapper.writeValueAsString(root);
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
