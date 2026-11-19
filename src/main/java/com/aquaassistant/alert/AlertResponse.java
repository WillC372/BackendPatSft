package com.aquaassistant.alert;

import java.time.Instant;

public record AlertResponse(Long id, Long measurementId, String severity, String kind, String title, String message,
                            boolean read, Instant createdAt) {

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getMeasurementId(), alert.getSeverity(), alert.getKind(), alert.getTitle(),
            alert.getMessage(), alert.isRead(), alert.getCreatedAt());
    }
}
