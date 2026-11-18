package com.aquaassistant.device.connection;

import com.aquaassistant.device.hardware.MeterProtocol;
import java.time.Instant;

public record DeviceSnapshot(DeviceState state, Long ownerId, Long deviceId, String deviceName, String port,
                             MeterProtocol protocol, boolean simulated, boolean supportsCalibration,
                             Instant connectedAt, String lastError) {
}
