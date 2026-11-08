package com.aquasense.intelligence.scenario;

import com.aquasense.common.BusinessException;
import com.aquasense.intelligence.advice.Advice;
import com.aquasense.intelligence.advice.AdviceStrategyResolver;
import com.aquasense.intelligence.agent.Verification;
import com.aquasense.intelligence.ai.AiAnswer;
import com.aquasense.intelligence.ai.AiPrompt;
import com.aquasense.intelligence.ai.AiProviderProxy;
import com.aquasense.intelligence.ai.AiUnavailableException;
import com.aquasense.intelligence.analysis.FigureCheck;
import com.aquasense.intelligence.analysis.TrendAnalyzer;
import com.aquasense.intelligence.analysis.TrendResult;
import com.aquasense.knowledge.Citation;
import com.aquasense.knowledge.GuidelineRetriever;
import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementService;
import com.aquasense.measurement.dto.MeasurementResponse;
import com.aquasense.profile.Classification;
import com.aquasense.profile.PhClassifier;
import com.aquasense.profile.PhStatus;
import com.aquasense.profile.ProfileService;
import com.aquasense.profile.WaterProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Explica con IA un escenario "¿qué pasaría si el pH fuera X?". El sistema calcula los hechos (clasificación,
 * cambio en la concentración de H+, tiempo para llegar con la tendencia actual, reglas del perfil y normas aplicables);
 * la IA redacta efectos, señales tempranas y acciones preventivas en JSON estricto; el sistema valida citas y cifras.
 * Si la IA no está disponible o responde mal, se genera la misma estructura con reglas.
 */
@Service
public class ScenarioExplainer {

    static final String SYSTEM_PROMPT = """
        Eres el motor de escenarios de AquaSense AI, un sistema que monitorea el pH del agua.
        El usuario quiere saber qué pasaría si el pH de su agua llegara a un valor hipotético. Recibes hechos ya calculados.
        Responde en español claro, en el JSON indicado:
        - headline: una frase que resuma el escenario.
        - explanation: 2 a 4 frases sobre qué significa ese pH para el uso del agua del perfil, comparado con el valor actual.
        - effects: efectos concretos por área (por ejemplo equipos, salud, cultivos, peces, desinfección, sabor), con severidad.
        - earlySignals: señales que el usuario podría observar antes de medir (olor, sabor, depósitos, irritación, plantas...).
        - preventiveActions: acciones seguras para evitar llegar a ese valor; sin dosis químicas exactas.
        - citedSourceIds: ids de fragmentosNormativos en los que te apoyas. En el texto menciona solo la organización
          (por ejemplo "según la CDC"), nunca el id.
        Reglas: usa solo cifras que aparezcan en los hechos; no calcules cifras nuevas. No atribuyas a una norma algo que su
        fragmento no dice. El pH por sí solo no demuestra potabilidad. Es un escenario hipotético, no una medición.""";

    private static final String DISCLAIMER = "Escenario hipotético: no se guarda en el historial ni representa una medición.";
    private static final int MAX_TEXT = 600;
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final MeasurementService measurementService;
    private final ProfileService profileService;
    private final PhClassifier classifier;
    private final TrendAnalyzer trendAnalyzer;
    private final AdviceStrategyResolver adviceResolver;
    private final AiProviderProxy aiProvider;
    private final GuidelineRetriever guidelines;
    private final ObjectMapper mapper;

    public ScenarioExplainer(MeasurementService measurementService, ProfileService profileService, PhClassifier classifier,
                             TrendAnalyzer trendAnalyzer, AdviceStrategyResolver adviceResolver, AiProviderProxy aiProvider,
                             GuidelineRetriever guidelines, ObjectMapper mapper) {
        this.measurementService = measurementService;
        this.profileService = profileService;
        this.classifier = classifier;
        this.trendAnalyzer = trendAnalyzer;
        this.adviceResolver = adviceResolver;
        this.aiProvider = aiProvider;
        this.guidelines = guidelines;
        this.mapper = mapper;
    }

    public ScenarioExplanation explain(Long userId, double hypotheticalPh) {
        Facts facts = facts(userId, hypotheticalPh);
        if (!aiProvider.isAvailable()) {
            return ruleBased(facts, "La IA generativa no está configurada; se muestra la explicación por reglas.");
        }
        try {
            AiAnswer answer = aiProvider.complete(new AiPrompt(userId, "SCENARIO", SYSTEM_PROMPT, factsJson(facts),
                ScenarioSchema.NAME, ScenarioSchema.forSources(facts.citations().stream().map(Citation::id).toList())));
            return fromAi(facts, answer);
        } catch (AiUnavailableException ex) {
            return ruleBased(facts, "IA no disponible en este momento; se muestra la explicación por reglas. Detalle: " + ex.getMessage());
        } catch (InvalidExplanationException ex) {
            return ruleBased(facts, "La respuesta de la IA no superó la validación (" + ex.getMessage() + "); se muestra la explicación por reglas.");
        } catch (BusinessException ex) {
            if (ex.getStatus() != HttpStatus.TOO_MANY_REQUESTS) {
                throw ex;
            }
            return ruleBased(facts, ex.getMessage());
        }
    }

    Facts facts(Long userId, double hypotheticalPh) {
        WaterProfile profile = profileService.activeProfile(userId);
        Double current = measurementService.latest(userId).map(MeasurementResponse::ph).orElse(null);
        Classification hypothetical = classifier.classify(hypotheticalPh, profile);
        Classification currentClass = current == null ? null : classifier.classify(current, profile);
        Instant now = Instant.now();
        List<Measurement> week = measurementService.between(userId, now.minus(Duration.ofDays(7)), now);
        TrendResult trend = trendAnalyzer.analyze(week, List.of());
        Double factor = current == null ? null : hydrogenFactor(current, hypotheticalPh);
        Advice advice = adviceResolver.forType(profile.getType()).advise(hypothetical, profile);
        List<Citation> citations = guidelines.forReading(profile, hypothetical, 4);
        return new Facts(profile, current, currentClass, hypotheticalPh, hypothetical, trend, factor,
            timeToReach(current, hypotheticalPh, trend), advice, citations);
    }

    /** Cuántas veces cambia la concentración de H+ (calculado por el sistema, con 3 cifras significativas). */
    static double hydrogenFactor(double current, double hypothetical) {
        double factor = Math.pow(10, current - hypothetical);
        return new java.math.BigDecimal(factor).round(new java.math.MathContext(3)).doubleValue();
    }

    static String timeToReach(Double current, double target, TrendResult trend) {
        if (current == null) {
            return "Sin medición actual: no se puede estimar cuándo se llegaría a este valor.";
        }
        double delta = target - current;
        if (Math.abs(delta) < 0.01) {
            return "El valor hipotético coincide con la medición actual.";
        }
        if (!trend.enoughData()) {
            return "No hay suficientes mediciones de los últimos 7 días para estimar cuándo se llegaría a este valor.";
        }
        double slope = trend.slopePerDay();
        if (Math.abs(slope) < 0.005) {
            return "La tendencia de los últimos 7 días es estable: el agua no se dirige a este valor por sí sola.";
        }
        if (Math.signum(slope) != Math.signum(delta)) {
            return String.format(Locale.US, "Con la tendencia actual (%+.3f pH por día) el agua no se dirige a este valor.", slope);
        }
        double days = delta / slope;
        return days > 60
            ? String.format(Locale.US, "Con la tendencia actual (%+.3f pH por día) tardaría más de 60 días.", slope)
            : String.format(Locale.US, "Con la tendencia actual (%+.3f pH por día) llegaría en unos %.0f días, si nada cambia.",
                slope, Math.ceil(days));
    }

    String factsJson(Facts f) {
        Map<String, Object> root = new LinkedHashMap<>();
        WaterProfile p = f.profile();
        root.put("perfil", Map.of("nombre", p.getName(), "tipo", p.getType().label(),
            "rangoIdeal", List.of(p.getIdealMin(), p.getIdealMax()), "rangoPrecaucion", List.of(p.getCautionMin(), p.getCautionMax())));
        if (f.current() != null) {
            root.put("actual", Map.of("ph", f.current(), "estado", f.currentClass().status().label()));
            root.put("cambio", round(f.hypothetical() - f.current(), 2));
            root.put("factorConcentracionHidrogeno", f.hydrogenFactor());
            root.put("explicacionFactor", "Concentración de iones H+ del escenario dividida entre la actual (calculada por el sistema).");
        } else {
            root.put("actual", "Sin mediciones");
        }
        root.put("hipotetico", Map.of("ph", f.hypothetical(), "naturaleza", f.hypotheticalClass().nature().label(),
            "estado", f.hypotheticalClass().status().label(), "explicacion", f.hypotheticalClass().explanation()));
        root.put("tendencia7Dias", f.trend().enoughData()
            ? Map.of("pendientePhPorDia", round(f.trend().slopePerDay(), 3), "promedio", round(f.trend().mean(), 2),
                "direccion", f.trend().directionLabel())
            : Map.of("suficientesDatos", false));
        root.put("tiempoParaLlegar", f.timeToReach());
        root.put("reglasDelPerfil", Map.of("resumen", f.advice().summary(), "causasConocidas", f.advice().possibleCauses(),
            "acciones", f.advice().actions()));
        root.put("fragmentosNormativos", f.citations().stream().map(c -> Map.of("id", c.id(), "organizacion", c.organization(),
            "seccion", c.section(), "citaLiteral", c.quote(), "resumen", c.summaryEs())).toList());
        if (f.citations().isEmpty()) {
            root.put("coberturaNormativa", guidelines.coverage(p.getType()));
        }
        try {
            return "Hechos del escenario (JSON):\n" + mapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    ScenarioExplanation fromAi(Facts f, AiAnswer answer) {
        JsonNode root;
        try {
            root = mapper.readTree(answer.text());
        } catch (Exception ex) {
            throw new InvalidExplanationException("la respuesta no es JSON");
        }
        String headline = text(root.path("headline"), "headline");
        String explanation = text(root.path("explanation"), "explanation");
        List<ScenarioExplanation.Effect> effects = new ArrayList<>();
        for (JsonNode e : list(root.path("effects"), "effects", 1, 5)) {
            ScenarioExplanation.Severity severity;
            try {
                severity = ScenarioExplanation.Severity.valueOf(e.path("severity").asText());
            } catch (IllegalArgumentException ex) {
                throw new InvalidExplanationException("severidad no permitida");
            }
            effects.add(new ScenarioExplanation.Effect(text(e.path("area"), "area"), text(e.path("effect"), "effect"), severity));
        }
        List<String> signals = new ArrayList<>();
        for (JsonNode s : list(root.path("earlySignals"), "earlySignals", 0, 4)) {
            signals.add(text(s, "earlySignals"));
        }
        List<String> actions = new ArrayList<>();
        for (JsonNode a : list(root.path("preventiveActions"), "preventiveActions", 1, 5)) {
            actions.add(text(a, "preventiveActions"));
        }
        List<String> adjustments = new ArrayList<>();
        Set<String> offered = new HashSet<>(f.citations().stream().map(Citation::id).toList());
        List<String> cited = new ArrayList<>();
        root.path("citedSourceIds").forEach(id -> {
            String value = id.asText("").trim();
            if (offered.contains(value)) {
                if (!cited.contains(value)) {
                    cited.add(value);
                }
            } else if (!value.isEmpty()) {
                adjustments.add("Se descartó la cita \"" + value + "\" porque no estaba entre los fragmentos entregados.");
            }
        });
        if (f.hypotheticalClass().status() == PhStatus.CRITICAL
            && effects.stream().noneMatch(e -> e.severity() == ScenarioExplanation.Severity.CRITICAL)) {
            ScenarioExplanation.Effect first = effects.get(0);
            effects.set(0, new ScenarioExplanation.Effect(first.area(), first.effect(), ScenarioExplanation.Severity.CRITICAL));
            adjustments.add("Severidad del primer efecto elevada a crítica porque el pH hipotético está fuera del rango de precaución.");
        }
        StringBuilder all = new StringBuilder(headline).append(' ').append(explanation);
        effects.forEach(e -> all.append(' ').append(e.effect()));
        signals.forEach(s -> all.append(' ').append(s));
        actions.forEach(a -> all.append(' ').append(a));
        Verification verification = FigureCheck.check(all.toString(), allowedFigures(f));
        return new ScenarioExplanation("AI", "Explicado por IA y validado por el sistema", answer.provider(), answer.model(),
            f.profile().getName(), f.current(), f.hypothetical(), f.hypotheticalClass().status().label(), f.hydrogenFactor(),
            f.timeToReach(), headline, explanation, effects, signals, actions, guidelines.resolve(cited),
            f.citations().isEmpty() ? guidelines.coverage(f.profile().getType()) : null, verification, adjustments,
            answer.cached() ? "Respuesta reutilizada de la caché (no consumió cuota)." : null, DISCLAIMER);
    }

    ScenarioExplanation ruleBased(Facts f, String note) {
        Classification h = f.hypotheticalClass();
        ScenarioExplanation.Severity severity = switch (h.status()) {
            case OPTIMAL -> ScenarioExplanation.Severity.INFO;
            case CAUTION -> ScenarioExplanation.Severity.CAUTION;
            case CRITICAL -> ScenarioExplanation.Severity.CRITICAL;
        };
        List<ScenarioExplanation.Effect> effects = new ArrayList<>();
        effects.add(new ScenarioExplanation.Effect("Estado del agua", h.explanation(), severity));
        f.citations().stream().limit(2).forEach(c -> effects.add(new ScenarioExplanation.Effect(c.organization(), c.summaryEs(),
            severity)));
        String headline = String.format(Locale.US, "Con pH %.2f el agua estaría en estado \"%s\" para %s.", f.hypothetical(),
            h.status().label().toLowerCase(Locale.ROOT), f.profile().getName());
        String explanation = f.advice().summary() + (f.hydrogenFactor() == null ? ""
            : String.format(Locale.US, " La concentración de iones H+ sería %s veces la actual.", f.hydrogenFactor()));
        List<String> actions = f.advice().actions().isEmpty()
            ? List.of("Mantener la frecuencia de medición.") : f.advice().actions().stream().limit(5).toList();
        return new ScenarioExplanation("RULES", "Generado con reglas", null, null, f.profile().getName(), f.current(),
            f.hypothetical(), h.status().label(), f.hydrogenFactor(), f.timeToReach(), headline, explanation, effects,
            List.of(), actions, f.citations().stream().limit(3).toList(),
            f.citations().isEmpty() ? guidelines.coverage(f.profile().getType()) : null, null, List.of(), note, DISCLAIMER);
    }

    static Set<Double> allowedFigures(Facts f) {
        Set<Double> values = new HashSet<>();
        WaterProfile p = f.profile();
        values.addAll(List.of(f.hypothetical(), p.getIdealMin(), p.getIdealMax(), p.getCautionMin(), p.getCautionMax()));
        if (f.current() != null) {
            values.add(f.current());
            values.add(Math.abs(round(f.hypothetical() - f.current(), 2)));
        }
        if (f.hydrogenFactor() != null) {
            values.add(f.hydrogenFactor());
        }
        if (f.trend().enoughData()) {
            values.add(Math.abs(round(f.trend().slopePerDay(), 3)));
            values.add(round(f.trend().mean(), 2));
        }
        StringBuilder texts = new StringBuilder(f.timeToReach());
        f.citations().forEach(c -> texts.append(' ').append(c.quote()).append(' ').append(c.summaryEs()));
        Matcher m = NUMBER.matcher(texts);
        while (m.find()) {
            values.add(Double.parseDouble(m.group()));
        }
        return values;
    }

    private static List<JsonNode> list(JsonNode node, String field, int min, int max) {
        if (!node.isArray() || node.size() < min || node.size() > max) {
            throw new InvalidExplanationException("lista inválida: " + field);
        }
        List<JsonNode> items = new ArrayList<>();
        node.forEach(items::add);
        return items;
    }

    private static String text(JsonNode node, String field) {
        String value = node.isTextual() ? node.asText().trim() : "";
        if (value.isEmpty()) {
            throw new InvalidExplanationException("texto vacío: " + field);
        }
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT - 1) + "…" : value;
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    record Facts(WaterProfile profile, Double current, Classification currentClass, double hypothetical,
                 Classification hypotheticalClass, TrendResult trend, Double hydrogenFactor, String timeToReach, Advice advice,
                 List<Citation> citations) {
    }

    static class InvalidExplanationException extends RuntimeException {
        InvalidExplanationException(String message) {
            super(message);
        }
    }
}
