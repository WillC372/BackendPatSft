package com.aquasense.measurement.dto;

import java.time.Instant;

public record MeasurementResponse(Long id, double ph, Double temperatureC, Instant measuredAt, String source,
                                  String sourceLabel, String nature, String natureLabel, String status,
                                  String statusLabel, String validationStatus, String validationLabel,
                                  String validationNotes, String explanation, Long profileId, String profileName,
                                  Long deviceId, String deviceName, String note) {
}
