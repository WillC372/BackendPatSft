package com.aquasense.measurement.validation;

public enum ValidationStatus {
    VALID("Validada"),
    WARNING("Validada con observaciones"),
    REJECTED("Rechazada");

    private final String label;

    ValidationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
