package com.aquasense.intelligence.agent;

import java.util.List;

/**
 * Informe entregado por el agente mediante la herramienta submit_report (ya validado).
 */
public record AgentReport(String title, String executiveSummary, List<Metric> keyMetrics, List<Section> sections,
                          List<Recommendation> recommendations, List<String> limitations, List<String> citedSourceIds) {

    public AgentReport {
        citedSourceIds = citedSourceIds == null ? List.of() : List.copyOf(citedSourceIds);
    }

    public AgentReport(String title, String executiveSummary, List<Metric> keyMetrics, List<Section> sections,
                       List<Recommendation> recommendations, List<String> limitations) {
        this(title, executiveSummary, keyMetrics, sections, recommendations, limitations, List.of());
    }

    /** Copia del informe con solo las citas permitidas. */
    public AgentReport withCitations(List<String> ids) {
        return new AgentReport(title, executiveSummary, keyMetrics, sections, recommendations, limitations, ids);
    }

    public record Metric(String label, String value) {
    }

    public record Section(String heading, String body, ChartKind chart) {
    }

    public record Recommendation(String action, int priority, String rationale) {
    }

    public enum ChartKind { NONE, PH_EVOLUTION, DISTRIBUTION, ANOMALIES, FORECAST }
}
