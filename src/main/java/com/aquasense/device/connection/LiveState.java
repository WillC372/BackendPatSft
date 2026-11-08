package com.aquasense.device.connection;

import com.aquasense.device.hardware.PhReading;

public class LiveState extends ConnectionState {

    @Override
    public DeviceState id() {
        return DeviceState.LIVE;
    }

    @Override
    public void disconnect(DeviceConnection connection) {
        connection.cancelLive();
        connection.closeMeter();
        connection.transitionTo(new DisconnectedState());
    }

    @Override
    public PhReading measure(DeviceConnection connection, int samples) {
        return connection.readStable(samples);
    }

    @Override
    public void stopLive(DeviceConnection connection) {
        connection.cancelLive();
        connection.transitionTo(new ConnectedState());
    }
}
