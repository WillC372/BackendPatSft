package com.aquasense.intelligence.engine;

import java.util.List;

public record AnalysisReport(String profileName, int measurementCount, int simulatedCount, boolean sufficientData,
                             Statistics statistics, List<AnomalyFinding> anomalies, TrendResult trend,
                             ForecastResult forecast, List<String> recommendations, List<String> limitations,
                             String disclaimer) {

    public static final String DISCLAIMER = "El pH es solo un indicador: por sí solo no demuestra que el agua sea "
            + "potable, segura ni esté libre de contaminantes.";
}
