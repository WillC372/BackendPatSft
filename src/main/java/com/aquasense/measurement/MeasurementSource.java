package com.aquasense.measurement;

public enum MeasurementSource {
    REAL("Medidor físico"),
    SIMULATED("Simulador (no es una medición real)"),
    MANUAL("Registro manual"),
    PHOTO("Foto analizada con IA");

    private final String label;

    MeasurementSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
