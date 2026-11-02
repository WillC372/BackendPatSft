package com.aquasense.intelligence.engine;

public record TrendResult(TrendDirection direction, double slopePerDay, double rSquared, double firstHalfMean,
                          double secondHalfMean, String summary) {
}
