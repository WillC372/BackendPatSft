package com.aquasense.vision;

import java.util.List;

/**
 * Lectura de una foto ya validada por {@link PhotoReadingValidator}.
 */
public record PhotoReading(boolean readable, PhotoKind kind, Double ph, Double phLow, Double phHigh, double confidence,
                           boolean referenceChartVisible, Double temperatureC, List<PhotoIssue> issues,
                           String observations, String provider, String model, List<String> adjustments) {
}
