package com.poolguard.patterns.observer;

import com.poolguard.model.SensorReading;

/**
 * Immutable data packet delivered by the (simulated) Bluetooth sensor.
 */
public final class SensorData {

    private final SensorReading reading;
    private final String deviceId;

    public SensorData(SensorReading reading, String deviceId) {
        this.reading = reading;
        this.deviceId = deviceId;
    }

    public SensorReading getReading() {
        return reading;
    }

    public String getDeviceId() {
        return deviceId;
    }
}
