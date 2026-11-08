package com.aquasense.intelligence.scenario;

import com.aquasense.intelligence.agent.Verification;
import com.aquasense.knowledge.Citation;
import java.util.List;

/**
 * Explanation of a hypothetical scenario. The computations ({@code hydrogenFactor}, {@code timeToReach}) are done by the system;
 * the AI only writes effects, signs and actions, and its figures are checked against the provided facts.
 */
public record ScenarioExplanation(String source, String sourceLabel, String provider, String model, String profileName,
                                  Double currentPh, double hypotheticalPh, String hypotheticalStatus, Double hydrogenFactor,
                                  String timeToReach, String headline, String explanation, List<Effect> effects,
                                  List<String> earlySignals, List<String> preventiveActions, List<Citation> citations,
                                  String coverageNote, Verification verification, List<String> adjustments, String note,
                                  String disclaimer) {

    public record Effect(String area, String effect, Severity severity) {
    }

    public enum Severity { INFO, CAUTION, CRITICAL }
}
