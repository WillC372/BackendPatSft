package com.aquasense.measurement.validation;

import java.time.Duration;

public class StaleTimestampHandler extends ValidationHandler {

    static final Duration MAX_DELAY = Duration.ofHours(24);

    @Override
    protected void check(ValidationContext context, ValidationOutcome outcome) {
        if (context.measuredAt().isBefore(context.now().minus(MAX_DELAY))) {
            outcome.flagSuspect("La medición se recibió con más de 24 horas de retraso.");
        }
    }
}
