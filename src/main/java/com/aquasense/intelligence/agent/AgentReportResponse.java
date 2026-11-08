package com.aquasense.intelligence.agent;

import com.aquasense.knowledge.Citation;
import java.time.Instant;
import java.util.List;

public record AgentReportResponse(Long id, Instant createdAt, String source, String sourceLabel, String provider,
                                  String model, int periodDays, String focus, AgentReport report,
                                  Verification verification, List<TraceStep> trace, int toolCalls, long durationMs,
                                  String note, String disclaimer, List<Citation> citations,
                                  List<String> discardedCitations) {

    public AgentReportResponse withId(Long newId) {
        return new AgentReportResponse(newId, createdAt, source, sourceLabel, provider, model, periodDays, focus, report,
            verification, trace, toolCalls, durationMs, note, disclaimer, citations, discardedCitations);
    }

    public record Summary(Long id, Instant createdAt, String source, String provider, int periodDays, String title) {
    }
}
