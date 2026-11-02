package com.aquasense.measurement.domain;

import java.util.UUID;

public record MeasurementRecorded(UUID measurementId, UUID ownerId, UUID deviceId, java.time.Instant measuredAt) {
}
