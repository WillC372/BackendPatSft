package com.aquasense.device.connection;

import com.aquasense.device.hardware.PhMeter;

public class DisconnectedState extends ConnectionState {

    @Override
    public DeviceState id() {
        return DeviceState.DISCONNECTED;
    }

    @Override
    public void connect(DeviceConnection connection, PhMeter meter, ConnectionSession session) {
        connection.openMeter(meter, session);
        connection.transitionTo(new ConnectedState());
    }
}
