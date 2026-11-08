package com.aquasense.device.connection;

import com.aquasense.device.hardware.PhReading;

/**
 * Observer de la conexión: recibe lecturas en vivo y cambios de estado del medidor.
 */
public interface DeviceListener {

    void onReading(ConnectionSession session, PhReading reading, boolean simulated);

    void onStateChanged(DeviceSnapshot snapshot);
}
