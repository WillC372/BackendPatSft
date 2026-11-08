package com.aquasense.device.connection;

import com.aquasense.common.BusinessException;
import com.aquasense.device.hardware.CalibrationPoint;
import com.aquasense.device.hardware.PhMeter;
import com.aquasense.device.hardware.PhReading;

/**
 * State: cada estado del medidor decide que operaciones son validas.
 * Las operaciones no permitidas responden con un mensaje claro en lugar de condicionales dispersos.
 */
public abstract class ConnectionState {

    public abstract DeviceState id();

    public void connect(DeviceConnection connection, PhMeter meter, ConnectionSession session) {
        throw notAllowed("conectar");
    }

    public void disconnect(DeviceConnection connection) {
        throw notAllowed("desconectar");
    }

    public PhReading measure(DeviceConnection connection, int samples) {
        throw notAllowed("medir");
    }

    public void startLive(DeviceConnection connection) {
        throw notAllowed("iniciar la medición en tiempo real");
    }

    public void stopLive(DeviceConnection connection) {
        throw notAllowed("detener la medición en tiempo real");
    }

    public String calibrate(DeviceConnection connection, CalibrationPoint point, double buffer) {
        throw notAllowed("calibrar");
    }

    private BusinessException notAllowed(String action) {
        return BusinessException.conflict("No se puede " + action + " con el medidor en estado \""
            + id().label() + "\".");
    }
}
