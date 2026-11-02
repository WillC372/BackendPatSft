package com.poolguard.analytics;

import com.poolguard.model.SensorReading;

import java.util.List;

/**
 * Analytical engine for the pH meter data stream.
 *
 * Computes statistics over a sliding window, estimates short-term trends
 * (linear regression) and detects anomalous pH drops BEFORE they reach
 * critical levels.
 */
public class AnalyticsEngine {

    /** Sliding window of the most recent readings (5 min at 2 s interval). */
    private static final int WINDOW_SIZE = 150;

    private final java.util.List<SensorReading> window = new java.util.ArrayList<>();

    /** Adds a reading to the sliding window. */
    public void addReading(SensorReading reading) {
        window.add(reading);
        while (window.size() > WINDOW_SIZE) {
            window.remove(0);
        }
    }

    public double averagePh() {
        return window.stream().mapToDouble(SensorReading::getPh).average().orElse(0.0);
    }

    public double minPh() {
        return window.stream().mapToDouble(SensorReading::getPh).min().orElse(0.0);
    }

    public double maxPh() {
        return window.stream().mapToDouble(SensorReading::getPh).max().orElse(0.0);
    }

    public double averageTemperature() {
        return window.stream().mapToDouble(SensorReading::getTemperatureC).average().orElse(0.0);
    }

    public double averageChlorine() {
        return window.stream().mapToDouble(SensorReading::getChlorinePpm).average().orElse(0.0);
    }

    /**
     * Linear regression slope of pH over time, expressed in pH units per hour.
     * Negative values mean the pH is falling.
     */
    public double phSlopePerHour() {
        int n = window.size();
        if (n < 5) {
            return 0.0;
        }
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        long t0 = window.get(0).getTimestamp();
        for (SensorReading r : window) {
            double x = (r.getTimestamp() - t0) / 3_600_000.0; // hours
            double y = r.getPh();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double denominator = n * sumXX - sumX * sumX;
        if (denominator == 0) {
            return 0.0;
        }
        double slopePerHour = (n * sumXY - sumX * sumY) / denominator;
        return slopePerHour; // x is already in hours
    }

    /**
     * Detects anomalous pH drops (faster than 0.15 pH units per hour) so the
     * system can warn before the water turns critical.
     */
    public boolean isAnomalousPhDrop() {
        return phSlopePerHour() < -0.15;
    }

    /** Statistics over an arbitrary list of readings (e.g. last 24 hours). */
    public java.util.Map<String, Object> stats(List<SensorReading> readings) {
        java.util.Map<String, Object> stats = new java.util.LinkedHashMap<>();
        if (readings.isEmpty()) {
            stats.put("count", 0);
            return stats;
        }
        double minPh = readings.stream().mapToDouble(SensorReading::getPh).min().orElse(0);
        double maxPh = readings.stream().mapToDouble(SensorReading::getPh).max().orElse(0);
        double avgPh = readings.stream().mapToDouble(SensorReading::getPh).average().orElse(0);
        double minCl = readings.stream().mapToDouble(SensorReading::getChlorinePpm).min().orElse(0);
        double maxCl = readings.stream().mapToDouble(SensorReading::getChlorinePpm).max().orElse(0);
        double avgCl = readings.stream().mapToDouble(SensorReading::getChlorinePpm).average().orElse(0);
        double avgTemp = readings.stream().mapToDouble(SensorReading::getTemperatureC).average().orElse(0);
        stats.put("count", readings.size());
        stats.put("minPh", minPh);
        stats.put("maxPh", maxPh);
        stats.put("avgPh", avgPh);
        stats.put("minChlorine", minCl);
        stats.put("maxChlorine", maxCl);
        stats.put("avgChlorine", avgCl);
        stats.put("avgTemperature", avgTemp);
        return stats;
    }
}
