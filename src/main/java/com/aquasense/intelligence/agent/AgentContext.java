package com.aquasense.intelligence.agent;

/**
 * Fixed context of an agent run. The user is set by the server, never by the model:
 * the tools can only read data of this user and within the limits of their plan.
 */
public record AgentContext(Long userId, int periodDays, int maxDays, int maxHorizon) {

    public int clampDays(int requested) {
        return Math.max(1, Math.min(requested <= 0 ? periodDays : requested, maxDays));
    }
}
