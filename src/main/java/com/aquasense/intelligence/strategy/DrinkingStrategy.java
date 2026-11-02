package com.aquasense.intelligence.strategy;

import java.time.Duration;

public class DrinkingStrategy implements AnalysisStrategy {

    public double outlierRobustZ() { return 3.0; }
    public double abruptChangePh() { return 0.8; }
    public Duration abruptChangeWindow() { return Duration.ofHours(24); }
    public double significantTrendPerDay() { return 0.1; }
    public String context() { return "agua de consumo"; }
}
