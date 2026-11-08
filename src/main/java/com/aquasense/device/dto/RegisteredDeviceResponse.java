package com.aquasense.device.dto;

import java.time.Instant;

public record RegisteredDeviceResponse(Long id, String name, String protocol, String protocolLabel, String port,
                                       boolean simulated, Instant lastConnectedAt) {
}
