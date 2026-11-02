package com.aquasense.measurement.validation;

import java.time.Duration;

public class FutureTimestampHandler extends ValidationHandler {

    static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(5);

    @Override
    protected void check(ValidationContext context, ValidationOutcome outcome) {
        if (context.measuredAt().isAfter(context.now().plus(CLOCK_TOLERANCE))) {
            outcome.reject("La fecha de la medición está en el futuro. Revise el reloj del dispositivo.");
        }
    }
}
