package com.aquasense.profile;

/**
 * Chemical nature of the value. The neutral band 6.8-7.2 is a convention of the application
 * for field readings; the exact neutral point is 7.0 at 25 degrees C.
 */
public enum PhNature {
    ACIDIC("Ácida"),
    NEUTRAL("Neutra"),
    ALKALINE("Alcalina");

    public static final double NEUTRAL_MIN = 6.8;
    public static final double NEUTRAL_MAX = 7.2;

    private final String label;

    PhNature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PhNature of(double ph) {
        if (ph < NEUTRAL_MIN) {
            return ACIDIC;
        }
        return ph > NEUTRAL_MAX ? ALKALINE : NEUTRAL;
    }
}
