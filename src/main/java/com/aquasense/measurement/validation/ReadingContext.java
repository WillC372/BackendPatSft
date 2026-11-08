package com.aquasense.measurement.validation;

import com.aquasense.measurement.Measurement;
import com.aquasense.measurement.MeasurementSource;
import java.time.Instant;
import java.util.Optional;

public record ReadingContext(double ph, Double temperatureC, Instant measuredAt, MeasurementSource source,
                             Optional<Measurement> previous) {
}
