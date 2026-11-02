package com.aquasense.waterprofile.domain;

import java.math.BigDecimal;

public enum AcidityClass {
    ACIDIC,
    NEUTRAL,
    ALKALINE;

    private static final BigDecimal NEUTRAL_MIN = new BigDecimal("6.5");
    private static final BigDecimal NEUTRAL_MAX = new BigDecimal("7.5");

    public static AcidityClass of(BigDecimal ph) {
        if (ph.compareTo(NEUTRAL_MIN) < 0) {
            return ACIDIC;
        }
        if (ph.compareTo(NEUTRAL_MAX) > 0) {
            return ALKALINE;
        }
        return NEUTRAL;
    }
}
