package com.aquasense.device.connection;

public enum DeviceState {
    DISCONNECTED("Desconectado"),
    CONNECTED("Conectado"),
    LIVE("Midiendo en tiempo real"),
    ERROR("Error de comunicación");

    private final String label;

    DeviceState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
