package com.aquasense.intelligence.diagnosis;

import com.aquasense.profile.PhStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validates the AI answer before showing it and applies safeguards based on the facts:
 * the confidence cannot exceed what the data allows and the risk cannot be lower
 * than the one indicated by the last measurement classified by the system.
 */
@Component
public class DiagnosisValidator {

    private static final int MAX_TEXT = 400;

    private final ObjectMapper mapper;

    public DiagnosisValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Result validate(String json, DiagnosisFacts facts) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception ex) {
            throw new InvalidDiagnosisException("La respuesta no es JSON.");
        }
        List<String> adjustments = new ArrayList<>();
        RiskLevel risk = enumValue(RiskLevel.class, root.path("riskLevel"), "riskLevel");
        String summary = text(root.path("summary"), "summary");
        List<Diagnosis.Finding> findings = new ArrayList<>();
        for (JsonNode node : list(root.path("findings"), "findings", 1, 6)) {
            findings.add(new Diagnosis.Finding(text(node.path("title"), "title"), text(node.path("evidence"), "evidence"),
                enumValue(Diagnosis.Severity.class, node.path("severity"), "severity")));
        }
        List<Diagnosis.Cause> causes = new ArrayList<>();
        for (JsonNode node : list(root.path("probableCauses"), "probableCauses", 0, 5)) {
            causes.add(new Diagnosis.Cause(text(node.path("cause"), "cause"),
                enumValue(Diagnosis.Likelihood.class, node.path("likelihood"), "likelihood"), text(node.path("evidence"), "evidence")));
        }
        List<Diagnosis.Action> actions = new ArrayList<>();
        for (JsonNode node : list(root.path("actions"), "actions", 1, 6)) {
            int priority = node.path("priority").asInt(0);
            if (priority < 1 || priority > 3) {
                throw new InvalidDiagnosisException("Prioridad fuera de rango.");
            }
            actions.add(new Diagnosis.Action(text(node.path("action"), "action"), priority, text(node.path("timeframe"), "timeframe")));
        }
        actions.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
        List<String> gaps = new ArrayList<>();
        for (JsonNode node : list(root.path("dataGaps"), "dataGaps", 0, 5)) {
            gaps.add(text(node, "dataGaps"));
        }
        if (!root.path("confidence").isNumber()) {
            throw new InvalidDiagnosisException("Falta la confianza.");
        }
        double confidence = Math.max(0, Math.min(1, root.path("confidence").asDouble()));
        int nextHours = root.path("nextMeasurementHours").asInt(0);
        if (nextHours < 1 || nextHours > 168) {
            throw new InvalidDiagnosisException("Horas hasta la próxima medición fuera de rango.");
        }

        java.util.Set<String> offered = new java.util.HashSet<>(facts.guidelineIds());
        List<String> cited = new ArrayList<>();
        JsonNode citedNode = root.path("citedSourceIds");
        if (citedNode.isArray()) {
            for (JsonNode node : citedNode) {
                String id = node.asText("").trim();
                if (offered.contains(id)) {
                    if (!cited.contains(id)) {
                        cited.add(id);
                    }
                } else if (!id.isEmpty()) {
                    adjustments.add("Se descartó la cita \"" + id + "\" porque no estaba entre los fragmentos normativos entregados.");
                }
            }
        }

        double maxConfidence = maxConfidence(facts);
        if (confidence > maxConfidence) {
            adjustments.add(String.format(Locale.US, "Confianza ajustada de %.2f a %.2f por la cantidad o el tipo de datos.",
                confidence, maxConfidence));
            confidence = maxConfidence;
        }
        RiskLevel floor = minimumRisk(facts);
        if (risk.ordinal() < floor.ordinal()) {
            adjustments.add("Riesgo elevado de \"" + risk.label() + "\" a \"" + floor.label()
                + "\" porque la última medición está en estado " + facts.latest().status().label().toLowerCase(Locale.ROOT) + ".");
            risk = floor;
        }
        if (facts.includesSimulated() && gaps.stream().noneMatch(g -> g.toLowerCase(Locale.ROOT).contains("simul"))) {
            gaps.add("Parte de las mediciones del periodo proviene del simulador y no representa agua real.");
        }
        if (risk == RiskLevel.CRITICAL || risk == RiskLevel.HIGH) {
            nextHours = Math.min(nextHours, risk == RiskLevel.CRITICAL ? 4 : 12);
        }
        Diagnosis diagnosis = new Diagnosis(risk, summary, findings, causes, actions, gaps,
            Math.round(confidence * 100) / 100.0, nextHours, cited);
        return new Result(diagnosis, adjustments);
    }

    static double maxConfidence(DiagnosisFacts facts) {
        double max = facts.count() < 5 ? 0.4 : facts.count() < 10 ? 0.6 : 0.9;
        if (facts.includesSimulated()) {
            max = Math.min(max, 0.5);
        }
        return max;
    }

    static RiskLevel minimumRisk(DiagnosisFacts facts) {
        if (facts.latest() == null) {
            return RiskLevel.LOW;
        }
        return facts.latest().status() == PhStatus.CRITICAL ? RiskLevel.HIGH
            : facts.latest().status() == PhStatus.CAUTION ? RiskLevel.MODERATE : RiskLevel.LOW;
    }

    private static List<JsonNode> list(JsonNode node, String field, int min, int max) {
        if (!node.isArray() || node.size() < min || node.size() > max) {
            throw new InvalidDiagnosisException("Lista inválida: " + field);
        }
        List<JsonNode> items = new ArrayList<>();
        node.forEach(items::add);
        return items;
    }

    private static String text(JsonNode node, String field) {
        String value = node.isTextual() ? node.asText().trim() : "";
        if (value.isEmpty()) {
            throw new InvalidDiagnosisException("Texto vacío: " + field);
        }
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT - 1) + "…" : value;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String field) {
        try {
            return Enum.valueOf(type, node.asText());
        } catch (IllegalArgumentException ex) {
            throw new InvalidDiagnosisException("Valor no permitido en " + field + ": " + node.asText());
        }
    }

    public record Result(Diagnosis diagnosis, List<String> adjustments) {
    }

    public static class InvalidDiagnosisException extends RuntimeException {
        public InvalidDiagnosisException(String message) {
            super(message);
        }
    }
}
