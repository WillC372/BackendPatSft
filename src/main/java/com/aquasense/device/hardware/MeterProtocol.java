package com.aquasense.device.hardware;

public enum MeterProtocol {
    SIMULATOR("Simulador AquaSense (no es un medidor real)"),
    ATLAS_EZO("Atlas Scientific EZO-pH (UART)"),
    AQUASENSE_ESP32("Firmware AquaSense ESP32");

    private final String label;

    MeterProtocol(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
