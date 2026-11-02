package com.aquasense.intelligence.engine;

import java.time.Instant;

public record AnomalyFinding(FindingType type, Severity severity, Instant measuredAt, double ph, String message,
                             String explanation) {

    public static final String NOT_A_DIAGNOSIS = "Es una anomalía estadística: no confirma por sí sola un riesgo "
            + "sanitario. Repita la medición y verifique con otros análisis.";
}
