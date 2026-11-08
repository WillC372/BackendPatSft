package com.aquasense.intelligence.agent;

import java.util.Map;

/**
 * Recorded step of the agent investigation: which tool it used, with which arguments and what it got.
 */
public record TraceStep(int step, String tool, String label, Map<String, Object> arguments, String summary,
                        long durationMs, String status) {
}
