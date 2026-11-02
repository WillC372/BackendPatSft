package com.aquasense.intelligence.strategy;

import java.time.Duration;

public class GeneralStrategy implements AnalysisStrategy {

    public double outlierRobustZ() { return 3.0; }
    public double abruptChangePh() { return 1.0; }
    public Duration abruptChangeWindow() { return Duration.ofHours(12); }
    public double significantTrendPerDay() { return 0.1; }
    public String context() { return "análisis general"; }
}
