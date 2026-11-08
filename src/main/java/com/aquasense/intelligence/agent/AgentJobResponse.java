package com.aquasense.intelligence.agent;

import java.util.List;

public record AgentJobResponse(String jobId, String status, String phase, List<TraceStep> trace,
                               AgentReportResponse report, String error, long elapsedMs) {
}
