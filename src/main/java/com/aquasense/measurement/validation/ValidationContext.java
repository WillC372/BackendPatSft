package com.aquasense.measurement.validation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public record ValidationContext(BigDecimal ph, Instant measuredAt, Instant now, Optional<PreviousReading> previous) {

    public record PreviousReading(BigDecimal ph, Instant measuredAt) {
    }
}
