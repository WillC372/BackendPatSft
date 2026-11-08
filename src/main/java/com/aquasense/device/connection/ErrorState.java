package com.aquasense.device.connection;

import com.aquasense.device.hardware.PhMeter;

/**
 * After a communication failure, reconnecting (a new attempt) or disconnecting to release the port is allowed.
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
