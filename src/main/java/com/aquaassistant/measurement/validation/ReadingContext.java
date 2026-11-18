package com.aquaassistant.measurement.validation;

import com.aquaassistant.measurement.Measurement;
import com.aquaassistant.measurement.MeasurementSource;
import java.time.Instant;
import java.util.Optional;

public record ReadingContext(double ph, Double temperatureC, Instant measuredAt, MeasurementSource source,
                             Optional<Measurement> previous) {
}
