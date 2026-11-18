package com.aquaassistant.device.dto;

import com.aquaassistant.measurement.dto.MeasurementResponse;
import java.time.Instant;
import java.util.List;

public record ReadingPreviewResponse(String pendingId, double ph, Double temperatureC, Instant measuredAt,
                                     String source, String sourceLabel, String nature, String natureLabel,
                                     String status, String statusLabel, String explanation, String validationStatus,
                                     String validationLabel, List<String> validationMessages, String deviceName,
                                     String profileName, boolean saved, MeasurementResponse measurement) {
}
