package com.aquasense.intelligence.agent;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Valida el informe del agente y verifica sus cifras: cada número decimal entre 0 y 14 que aparezca en el texto
 * debe coincidir (con el redondeo usado) con algún valor devuelto por las herramientas. Los que no coinciden se reportan.
 */
@Component
public class AgentReportValidator {

    private static final Pattern DECIMAL = Pattern.compile("(?<![\\d.,])(\\d{1,2})[.,](\\d{1,3})(?![\\d])");
    private static final int MAX_BODY = 1800;
    private static final int MAX_SHORT = 300;

    public AgentReport parse(JsonNode args) {
        if (args == null || !args.isObject()) {
            throw new InvalidReportException("Los argumentos deben ser un objeto JSON.");
        }
        String title = text(args.path("title"), "title", MAX_SHORT);
        String summary = text(args.path("executiveSummary"), "executiveSummary", MAX_BODY);
        List<AgentReport.Metric> metrics = new ArrayList<>();
        for (JsonNode m : array(args.path("keyMetrics"), "keyMetrics", 0, 8)) {
            metrics.add(new AgentReport.Metric(text(m.path("label"), "keyMetrics.label", 80), text(m.path("value"), "keyMetrics.value", 80)));
        }
        List<AgentReport.Section> sections = new ArrayList<>();
        for (JsonNode s : array(args.path("sections"), "sections", 2, 8)) {
            AgentReport.ChartKind chart;
            try {
                chart = AgentReport.ChartKind.valueOf(s.path("chart").asText("NONE"));
            } catch (IllegalArgumentException ex) {
                chart = AgentReport.ChartKind.NONE;
            }
            sections.add(new AgentReport.Section(text(s.path("heading"), "sections.heading", 120),
                text(s.path("body"), "sections.body", MAX_BODY), chart));
        }
        List<AgentReport.Recommendation> recommendations = new ArrayList<>();
        for (JsonNode r : array(args.path("recommendations"), "recommendations", 1, 6)) {
            int priority = r.path("priority").asInt(2);
            recommendations.add(new AgentReport.Recommendation(text(r.path("action"), "recommendations.action", MAX_SHORT),
                Math.max(1, Math.min(3, priority)), r.path("rationale").asText("").trim()));
        }
        recommendations.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
        List<String> limitations = new ArrayList<>();
        JsonNode lim = args.path("limitations");
        if (lim.isArray()) {
            lim.forEach(l -> {
                if (l.isTextual() && !l.asText().isBlank() && limitations.size() < 6) {
                    limitations.add(trim(l.asText(), MAX_SHORT));
                }
            });
        }
        List<String> cited = new ArrayList<>();
        JsonNode ids = args.path("citedSourceIds");
        if (ids.isArray()) {
            ids.forEach(id -> {
                if (id.isTextual() && !id.asText().isBlank() && !cited.contains(id.asText().trim()) && cited.size() < 8) {
                    cited.add(id.asText().trim());
                }
            });
        }
        return new AgentReport(title, summary, metrics, sections, recommendations, limitations, cited);
    }

    public Verification verify(AgentReport report, Set<Double> facts) {
        StringBuilder text = new StringBuilder(report.executiveSummary()).append(' ');
        report.keyMetrics().forEach(m -> text.append(m.value()).append(' '));
        report.sections().forEach(s -> text.append(s.body()).append(' '));
        report.recommendations().forEach(r -> text.append(r.action()).append(' ').append(r.rationale()).append(' '));
        return com.aquasense.intelligence.analysis.FigureCheck.check(text.toString(), facts);
    }

    private static List<JsonNode> array(JsonNode node, String field, int min, int max) {
        if (!node.isArray() || node.size() < min) {
            throw new InvalidReportException("'" + field + "' debe tener al menos " + min + " elementos.");
        }
        List<JsonNode> items = new ArrayList<>();
        node.forEach(n -> {
            if (items.size() < max) {
                items.add(n);
            }
        });
        return items;
    }

    private static String text(JsonNode node, String field, int max) {
        String value = node.isTextual() ? node.asText().trim() : "";
        if (value.isEmpty()) {
            throw new InvalidReportException("Falta el texto de '" + field + "'.");
        }
        return trim(value, max);
    }

    private static String trim(String value, int max) {
        String clean = value.replace("**", "").trim();
        return clean.length() > max ? clean.substring(0, max - 1) + "…" : clean;
    }

    public static class InvalidReportException extends RuntimeException {
        public InvalidReportException(String message) {
            super(message);
        }
    }
}
