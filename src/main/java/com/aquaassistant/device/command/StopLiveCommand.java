package com.aquaassistant.device.command;

import com.aquaassistant.device.connection.DeviceConnection;
import com.aquaassistant.device.connection.DeviceState;

public record StopLiveCommand() implements DeviceCommand<DeviceState> {

    @Override
    public String name() {
        return "DEVICE_LIVE_STOP";
    }

    @Override
    public String describe() {
        return "Detención de medición en tiempo real";
    }

    @Override
    public DeviceState execute(DeviceConnection connection) {
        connection.stopLive();
        return connection.currentState();
    }
}
