package com.aquasense.device.hardware;

public enum CalibrationPoint {
    MID(7.00), LOW(4.00), HIGH(10.00);

    private final double defaultBuffer;

    CalibrationPoint(double defaultBuffer) {
        this.defaultBuffer = defaultBuffer;
    }

    public double defaultBuffer() {
        return defaultBuffer;
    }
}
