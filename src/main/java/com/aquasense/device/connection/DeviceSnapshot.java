package com.aquasense.device.connection;

import com.aquasense.device.hardware.MeterProtocol;
import java.time.Instant;

public record DeviceSnapshot(DeviceState state, Long ownerId, Long deviceId, String deviceName, String port,
                             MeterProtocol protocol, boolean simulated, boolean supportsCalibration,
                             Instant connectedAt, String lastError) {
}
