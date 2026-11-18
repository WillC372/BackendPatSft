package com.aquaassistant.measurement.dto;

import com.aquaassistant.measurement.MeasurementSource;
import java.time.Instant;

public record NewReading(double ph, Double temperatureC, Instant measuredAt, MeasurementSource source,
                         Long deviceId, String note) {
}
