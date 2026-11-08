package com.aquasense.intelligence.scenario;

import com.aquasense.intelligence.analysis.TrendResult;

/** Test access to the system computations in {@link ScenarioExplainer}. */
public final class ScenarioExplainerProbe {

    private ScenarioExplainerProbe() {
    }

    public static String timeToReach(Double current, double target, TrendResult trend) {
        return ScenarioExplainer.timeToReach(current, target, trend);
    }

    public static double hydrogenFactor(double current, double hypothetical) {
        return ScenarioExplainer.hydrogenFactor(current, hypothetical);
    }
}
