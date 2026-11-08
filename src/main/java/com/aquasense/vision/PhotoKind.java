package com.aquasense.vision;

public enum PhotoKind {
    TEST_STRIP("Tira reactiva"), METER_DISPLAY("Pantalla de medidor"), OTHER("Otra imagen");

    private final String label;

    PhotoKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
