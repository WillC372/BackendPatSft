package com.aquasense.device.connection;

import com.aquasense.device.hardware.CalibrationPoint;
import com.aquasense.device.hardware.PhReading;

public class ConnectedState extends ConnectionState {

    @Override
    public DeviceState id() {
        return DeviceState.CONNECTED;
    }

    @Override
    public void disconnect(DeviceConnection connection) {
        connection.closeMeter();
        connection.transitionTo(new DisconnectedState());
    }

    @Override
    public PhReading measure(DeviceConnection connection, int samples) {
        return connection.readStable(samples);
    }

    @Override
    public void startLive(DeviceConnection connection) {
        connection.scheduleLive();
        connection.transitionTo(new LiveState());
    }

    @Override
    public String calibrate(DeviceConnection connection, CalibrationPoint point, double buffer) {
        return connection.calibrateMeter(point, buffer);
    }
}
