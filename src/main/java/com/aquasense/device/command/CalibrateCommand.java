package com.aquasense.device.command;

import com.aquasense.device.connection.DeviceConnection;
import com.aquasense.device.hardware.CalibrationPoint;
import java.util.Locale;

public record CalibrateCommand(CalibrationPoint point, double buffer) implements DeviceCommand<String> {

    @Override
    public String name() {
        return "DEVICE_CALIBRATE";
    }

    @Override
    public String describe() {
        return String.format(Locale.US, "Calibración %s con buffer %.2f", point, buffer);
    }

    @Override
    public String execute(DeviceConnection connection) {
        return connection.calibrate(point, buffer);
    }
}
