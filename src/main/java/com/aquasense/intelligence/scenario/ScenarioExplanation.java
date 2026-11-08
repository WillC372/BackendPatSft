package com.aquasense.intelligence.scenario;

import com.aquasense.intelligence.agent.Verification;
import com.aquasense.knowledge.Citation;
import java.util.List;

/**
 * Explicación de un escenario hipotético. Los cálculos ({@code hydrogenFactor}, {@code timeToReach}) los hace el sistema;
 * la IA solo redacta efectos, señales y acciones, y sus cifras se verifican contra los hechos entregados.
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
