package com.aquasense.device.command;

import com.aquasense.device.connection.DeviceConnection;
import com.aquasense.device.connection.DeviceState;

public record StartLiveCommand() implements DeviceCommand<DeviceState> {

    @Override
    public String name() {
        return "DEVICE_LIVE_START";
    }

    @Override
    public String describe() {
        return "Inicio de medición en tiempo real";
    }

    @Override
    public DeviceState execute(DeviceConnection connection) {
        connection.startLive();
        return connection.currentState();
    }
}
