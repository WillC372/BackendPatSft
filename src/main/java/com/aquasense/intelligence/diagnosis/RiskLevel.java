package com.aquasense.intelligence.diagnosis;

public enum RiskLevel {
    LOW("Bajo"), MODERATE("Moderado"), HIGH("Alto"), CRITICAL("Crítico");

    private final String label;

    RiskLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public RiskLevel atLeast(RiskLevel other) {
        return ordinal() >= other.ordinal() ? this : other;
    }
}
