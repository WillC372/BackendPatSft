package com.aquasense.measurement.dto;

import com.aquasense.measurement.MeasurementSource;
import java.time.Instant;

public record NewReading(double ph, Double temperatureC, Instant measuredAt, MeasurementSource source,
                         Long deviceId, String note) {
}
