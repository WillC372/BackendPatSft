package com.aquasense.intelligence.strategy;

import java.time.Duration;

public class AquariumStrategy implements AnalysisStrategy {

    public double outlierRobustZ() { return 2.5; }
    public double abruptChangePh() { return 0.3; }
    public Duration abruptChangeWindow() { return Duration.ofHours(12); }
    public double significantTrendPerDay() { return 0.05; }
    public String context() { return "acuario"; }
}
