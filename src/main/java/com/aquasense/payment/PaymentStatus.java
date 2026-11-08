package com.aquasense.payment;

public enum PaymentStatus {
    PENDING("Pendiente"), APPROVED("Aprobado"), DECLINED("Rechazado"), ERROR("Error"), EXPIRED("Vencido");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
