package com.aquasense.intelligence.strategy;

import java.time.Duration;

public class PoolStrategy implements AnalysisStrategy {

    public double outlierRobustZ() { return 3.0; }
    public double abruptChangePh() { return 0.5; }
    public Duration abruptChangeWindow() { return Duration.ofHours(6); }
    public double significantTrendPerDay() { return 0.1; }
    public String context() { return "piscina"; }
}
