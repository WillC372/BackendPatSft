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
 * Explains with AI a "what if the pH were X?" scenario. The system computes the facts (classification,
 * change in the H+ concentration, time to get there with the current trend, profile rules and applicable guidelines);
 * the AI writes effects, early signs and preventive actions in strict JSON; the system validates citations and figures.
 * If the AI is not available or answers badly, the same structure is generated with rules.
 */
@Service
public class ScenarioExplainer {

    static final String SYSTEM_PROMPT = """
        You are the scenario engine of AquaSense AI, a system that monitors the pH of water.
        The user wants to know what would happen if the pH of their water reached a hypothetical value. You receive facts already computed.
        Answer in clear Spanish, in the JSON given:
        - headline: one sentence that sums up the scenario.
        - explanation: 2 to 4 sentences about what that pH means for the water use of the profile, compared with the current value.
        - effects: concrete effects by area (for example equipment, health, crops, fish, disinfection, taste), with severity.
        - earlySignals: signs the user could notice before measuring (smell, taste, deposits, irritation, plants...).
        - preventiveActions: safe actions to avoid reaching that value; no exact chemical doses.
        - citedSourceIds: ids from guidelineFragments you rely on. In the text mention only the organization
          (for example "según la CDC"), never the id.
        Rules: only use figures that appear in the facts; do not compute new figures. Do not attribute to a guideline anything its
        fragment does not say. pH alone does not prove that water is drinkable. It is a hypothetical scenario, not a measurement.""";

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

    /** How many times the H+ concentration changes (computed by the system, with 3 significant figures). */
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
        root.put("profile", Map.of("name", p.getName(), "type", p.getType().label(),
            "idealRange", List.of(p.getIdealMin(), p.getIdealMax()), "cautionRange", List.of(p.getCautionMin(), p.getCautionMax())));
        if (f.current() != null) {
            root.put("current", Map.of("ph", f.current(), "status", f.currentClass().status().label()));
            root.put("change", round(f.hypothetical() - f.current(), 2));
            root.put("hydrogenConcentrationFactor", f.hydrogenFactor());
            root.put("factorExplanation", "H+ ion concentration of the scenario divided by the current one (computed by the system).");
        } else {
            root.put("current", "No measurements");
        }
        root.put("hypothetical", Map.of("ph", f.hypothetical(), "nature", f.hypotheticalClass().nature().label(),
            "status", f.hypotheticalClass().status().label(), "explanation", f.hypotheticalClass().explanation()));
        root.put("trend7Days", f.trend().enoughData()
            ? Map.of("phSlopePerDay", round(f.trend().slopePerDay(), 3), "mean", round(f.trend().mean(), 2),
                "direction", f.trend().directionLabel())
            : Map.of("enoughData", false));
        root.put("timeToReach", f.timeToReach());
        root.put("profileRules", Map.of("summary", f.advice().summary(), "knownCauses", f.advice().possibleCauses(),
            "actions", f.advice().actions()));
        root.put("guidelineFragments", f.citations().stream().map(c -> Map.of("id", c.id(), "organization", c.organization(),
            "section", c.section(), "literalQuote", c.quote(), "summary", c.summaryEs())).toList());
        if (f.citations().isEmpty()) {
            root.put("guidelineCoverage", guidelines.coverage(p.getType()));
        }
        try {
            return "Scenario facts (JSON):\n" + mapper.writeValueAsString(root);
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
