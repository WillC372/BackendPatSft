package com.aquasense.device.connection;

import com.aquasense.device.hardware.PhMeter;

/**
 * Tras un fallo de comunicacion se permite reconectar (nuevo intento) o desconectar para liberar el puerto.
 */
public class ErrorState extends ConnectionState {

    @Override
    public DeviceState id() {
        return DeviceState.ERROR;
    }

    @Override
    public void connect(DeviceConnection connection, PhMeter meter, ConnectionSession session) {
        connection.closeMeter();
        connection.openMeter(meter, session);
        connection.transitionTo(new ConnectedState());
    }

    @Override
    public void disconnect(DeviceConnection connection) {
        connection.closeMeter();
        connection.transitionTo(new DisconnectedState());
    }
}
