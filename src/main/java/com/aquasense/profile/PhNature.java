package com.aquasense.profile;

/**
 * Naturaleza química del valor. La banda neutra 6.8-7.2 es una convencion de la aplicación
 * para lecturas de campo; el neutro exacto es 7.0 a 25 grados C.
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
