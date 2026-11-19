package com.aquaassistant.measurement;

public enum MeasurementSource {
    REAL("Medidor físico"),
    SIMULATED("Simulador (no es una medición real)"),
    MANUAL("Registro manual"),
    PHOTO("Foto analizada con IA"),
    /** Real reading of a USGS station loaded into a place: real water, but not the person's own measurement. */
    OFFICIAL("Dato oficial del USGS");

    private final String label;

    MeasurementSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
