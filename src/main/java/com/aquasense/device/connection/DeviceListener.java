package com.aquasense.device.connection;

import com.aquasense.device.hardware.PhReading;

/**
 * Connection observer: receives live readings and state changes of the meter.
 */
public interface DeviceListener {

    void onReading(ConnectionSession session, PhReading reading, boolean simulated);

    void onStateChanged(DeviceSnapshot snapshot);
}
