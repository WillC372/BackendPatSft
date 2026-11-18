package com.aquaassistant.measurement.dto;

import java.time.Instant;

public record SeriesPoint(Long id, Instant measuredAt, double ph, String status, String source) {
}
