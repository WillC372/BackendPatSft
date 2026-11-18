package com.aquaassistant.device.hardware;

public enum MeterProtocol {
    SIMULATOR("Simulador Aqua Assistant (no es un medidor real)"),
    ATLAS_EZO("Atlas Scientific EZO-pH (UART)"),
    AQUASENSE_ESP32("Firmware Aqua Assistant ESP32"),
    TEXT_METER("Medidor con salida de texto (laboratorio)");

    private final String label;

    MeterProtocol(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
