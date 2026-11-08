package com.aquasense.intelligence.agent;

/**
 * Contexto fijo de una ejecución del agente. El usuario lo determina el servidor, nunca el modelo:
 * las herramientas solo pueden leer datos de este usuario y dentro de los límites de su plan.
 */
public record AgentContext(Long userId, int periodDays, int maxDays, int maxHorizon) {

    public int clampDays(int requested) {
        return Math.max(1, Math.min(requested <= 0 ? periodDays : requested, maxDays));
    }
}
