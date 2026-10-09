package com.poolguard.ai;

import com.poolguard.analytics.AnalyticsEngine;
import com.poolguard.model.SensorReading;

/**
 * Heuristic predictive model ("IA").
 *
 * Crosses current and historical variables - pH, chlorine, temperature,
 * bather load and the short-term pH trend - to estimate with a mathematical
 * probability when the water will collapse in the next hours
 * ("early saturation warning").
 */
public class PredictiveEngine {

    private final AnalyticsEngine analytics;

    public PredictiveEngine(AnalyticsEngine analytics) {
        this.analytics = analytics;
    }

    /** Evaluates the current situation and returns a prediction. */
    public Prediction evaluate(SensorReading latest, int batherLoad) {
        double ph = latest.getPh();
        double chlorine = latest.getChlorinePpm();
        double temp = latest.getTemperatureC();
        double slope = analytics.phSlopePerHour();

        // Individual risk factors, each normalized to 0..1
        double phRisk = clamp01((7.2 - ph) / 0.8) + clamp01((ph - 7.6) / 0.8);
        double chlorineRisk = clamp01((1.0 - chlorine) / 1.0);
        double tempRisk = clamp01((temp - 24.0) / 10.0);   // warm water degrades faster
        double loadRisk = clamp01(batherLoad / 40.0);     // bather load (adults + children)
        double trendRisk = clamp01(-slope / 0.5);          // falling pH trend

        // Weighted saturation score (0..100)
        double score = 100.0 * (0.30 * phRisk
                + 0.25 * chlorineRisk
                + 0.15 * tempRisk
                + 0.15 * loadRisk
                + 0.15 * trendRisk);

        // Logistic function -> probability of collapse in the next hours
        double probability = 1.0 / (1.0 + Math.exp(-(score - 50.0) / 12.0));

        int estimatedHours = probability > 0.5
                ? Math.max(1, (int) Math.round((1.0 - probability) * 12.0))
                : -1;

        String recommendation;
        if (probability >= 0.75) {
            recommendation = "Riesgo alto de saturación. Aumente el cloro y controle el pH de inmediato.";
        } else if (probability >= 0.5) {
            recommendation = "Riesgo moderado. Programe una dosificación preventiva en las próximas horas.";
        } else if (probability >= 0.25) {
            recommendation = "Riesgo bajo. Monitoree la carga de bañistas y mantenga la dosificación habitual.";
        } else {
            recommendation = "Sin riesgo de saturación. Operación normal.";
        }

        return new Prediction(score, probability, estimatedHours, recommendation);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** Result of the predictive evaluation. */
    public static final class Prediction {
        public final double saturationScore;
        public final double collapseProbability;
        public final int estimatedHoursUntilCritical; // -1 when not applicable
        public final String recommendationEs;

        public Prediction(double saturationScore, double collapseProbability,
                          int estimatedHoursUntilCritical, String recommendationEs) {
            this.saturationScore = saturationScore;
            this.collapseProbability = collapseProbability;
            this.estimatedHoursUntilCritical = estimatedHoursUntilCritical;
            this.recommendationEs = recommendationEs;
        }

        public double getSaturationScore() {
            return saturationScore;
        }

        public double getCollapseProbability() {
            return collapseProbability;
        }

        public int getEstimatedHoursUntilCritical() {
            return estimatedHoursUntilCritical;
        }

        public String getRecommendationEs() {
            return recommendationEs;
        }
    }
}
