package com.aquaassistant.measurement;

/**
 * Other water parameters measured together with the pH, all optional, in mg/L (ppm): free chlorine, total alkalinity
 * and calcium hardness (both as CaCO3) and cyanuric acid (chlorine stabilizer).
 */
public record WaterParams(Double freeChlorine, Double totalAlkalinity, Double calciumHardness, Double cyanuricAcid) {

    public static final WaterParams NONE = new WaterParams(null, null, null, null);

    public boolean isEmpty() {
        return freeChlorine == null && totalAlkalinity == null && calciumHardness == null && cyanuricAcid == null;
    }
}
