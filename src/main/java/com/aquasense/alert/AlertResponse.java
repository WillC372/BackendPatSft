package com.aquasense.alert;

import java.time.Instant;

public record AlertResponse(Long id, Long measurementId, String severity, String title, String message, boolean read,
                            Instant createdAt) {

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getMeasurementId(), alert.getSeverity(), alert.getTitle(),
            alert.getMessage(), alert.isRead(), alert.getCreatedAt());
    }
}
