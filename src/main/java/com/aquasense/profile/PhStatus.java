package com.aquasense.profile;

public enum PhStatus {
    OPTIMAL("En rango óptimo"),
    CAUTION("Precaución"),
    CRITICAL("Crítico");

    private final String label;

    PhStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
