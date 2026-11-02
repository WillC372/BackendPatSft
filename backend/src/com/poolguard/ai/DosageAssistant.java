package com.poolguard.ai;

import com.poolguard.model.SensorReading;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart dosing assistant.
 *
 * Calculates the exact amount of corrective chemicals (chlorine, soda ash,
 * muriatic acid) based on the pool volume and the current meter readings.
 *
 * Empirical factors (simplified for the case study):
 *   - Soda ash:        ~15 g  per m3 to raise pH by 0.1
 *   - Muriatic acid:   ~10 mL per m3 to lower pH by 0.1  (20% solution)
 *   - Sodium hypochlorite: ~10 mL per m3 to raise chlorine by 1 ppm (12%)
 */
public class DosageAssistant {

    private final double volumeM3;

    public DosageAssistant(double volumeM3) {
        this.volumeM3 = volumeM3;
    }

    /** Computes the list of dosing recommendations for the current reading. */
    public List<DosingRecommendation> compute(SensorReading r) {
        List<DosingRecommendation> list = new ArrayList<>();

        if (r.getPh() < 7.2) {
            double grams = volumeM3 * (7.4 - r.getPh()) * 15.0;
            list.add(new DosingRecommendation("Soda cáustica (carbonato de sodio)", round1(grams), "g", "Subir pH"));
        } else if (r.getPh() > 7.6) {
            double ml = volumeM3 * (r.getPh() - 7.4) * 10.0;
            list.add(new DosingRecommendation("Ácido clorhídrico (20%)", round1(ml), "mL", "Bajar pH"));
        }

        if (r.getChlorinePpm() < 1.0) {
            double ml = volumeM3 * (1.5 - r.getChlorinePpm()) * 10.0;
            list.add(new DosingRecommendation("Hipoclorito de sodio (12%)", round1(ml), "mL", "Subir cloro"));
        }

        if (list.isEmpty()) {
            list.add(new DosingRecommendation("Ninguna", 0, "-", "Agua en condiciones"));
        }
        return list;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** A single chemical dosing recommendation. */
    public static final class DosingRecommendation {
        public final String chemical;
        public final double quantity;
        public final String unit;
        public final String purpose;

        public DosingRecommendation(String chemical, double quantity, String unit, String purpose) {
            this.chemical = chemical;
            this.quantity = quantity;
            this.unit = unit;
            this.purpose = purpose;
        }

        public String getChemical() {
            return chemical;
        }

        public double getQuantity() {
            return quantity;
        }

        public String getUnit() {
            return unit;
        }

        public String getPurpose() {
            return purpose;
        }
    }
}
