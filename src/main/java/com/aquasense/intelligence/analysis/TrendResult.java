package com.aquasense.intelligence.analysis;

import java.util.Map;

public record TrendResult(boolean enoughData, String message, int count, double mean, double min, double max,
                          double stdDev, double slopePerDay, String direction, String directionLabel,
                          Double previousMean, Double changeVsPrevious, Map<String, Long> statusDistribution) {
}
