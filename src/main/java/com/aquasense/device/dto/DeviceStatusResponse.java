package com.aquasense.device.dto;

import java.time.Instant;

public record DeviceStatusResponse(String state, String stateLabel, boolean ownedByYou, Long deviceId,
                                   String deviceName, String port, String protocol, String protocolLabel,
                                   boolean simulated, boolean supportsCalibration, Instant connectedAt,
                                   String lastError) {
}
