package com.aquasense.device.dto;

public record DetectedDeviceResponse(String port, String name, String description, String transport,
                                     boolean simulated, String suggestedProtocol, Long registeredId) {
}
