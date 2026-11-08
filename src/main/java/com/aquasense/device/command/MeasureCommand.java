package com.aquasense.device.command;

import com.aquasense.device.connection.DeviceConnection;
import com.aquasense.device.hardware.PhReading;

public record MeasureCommand(int samples) implements DeviceCommand<PhReading> {

    @Override
    public String name() {
        return "DEVICE_MEASURE";
    }

    @Override
    public String describe() {
        return "Medición estabilizada con " + samples + " muestras";
    }

    @Override
    public PhReading execute(DeviceConnection connection) {
        return connection.measure(samples);
    }
}
