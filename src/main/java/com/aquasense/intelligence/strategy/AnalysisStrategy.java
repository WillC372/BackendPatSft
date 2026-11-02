package com.aquasense.intelligence.strategy;

import java.time.Duration;

public interface AnalysisStrategy {

    double outlierRobustZ();

    double abruptChangePh();

    Duration abruptChangeWindow();

    double significantTrendPerDay();

    String context();
}
