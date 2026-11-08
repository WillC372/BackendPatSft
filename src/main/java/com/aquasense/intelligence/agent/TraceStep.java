package com.aquasense.intelligence.agent;

import java.util.Map;

/**
 * Paso registrado de la investigación del agente: qué herramienta usó, con qué argumentos y qué obtuvo.
 */
public record TraceStep(int step, String tool, String label, Map<String, Object> arguments, String summary,
                        long durationMs, String status) {
}
