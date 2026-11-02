package com.aquasense.intelligence.strategy;

import java.time.Duration;

public class AgricultureStrategy implements AnalysisStrategy {

    public double outlierRobustZ() { return 3.5; }
    public double abruptChangePh() { return 1.0; }
    public Duration abruptChangeWindow() { return Duration.ofHours(24); }
    public double significantTrendPerDay() { return 0.15; }
    public String context() { return "riego"; }
}
