package com.aquasense.measurement.validation;

import java.time.Duration;
import java.time.Instant;

public class TimestampValidator extends ReadingValidator {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);
    private static final Duration MAX_AGE = Duration.ofDays(366);

    @Override
    protected void check(ReadingContext context, ValidationReport report) {
        Instant now = Instant.now();
        if (context.measuredAt().isAfter(now.plus(MAX_CLOCK_SKEW))) {
            report.reject("La fecha de la medición está en el futuro.");
        } else if (context.measuredAt().isBefore(now.minus(MAX_AGE))) {
            report.reject("La fecha de la medición tiene más de un año de antigüedad.");
        }
    }
}
