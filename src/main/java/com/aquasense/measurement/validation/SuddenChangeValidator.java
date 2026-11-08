package com.aquasense.measurement.validation;

import java.time.Duration;
import java.util.Locale;

public class SuddenChangeValidator extends ReadingValidator {

    public static final double MAX_DELTA = 1.0;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Override
    protected void check(ReadingContext context, ValidationReport report) {
        context.previous()
            .filter(previous -> Duration.between(previous.getMeasuredAt(), context.measuredAt()).abs().compareTo(WINDOW) <= 0)
            .filter(previous -> Math.abs(previous.getPh() - context.ph()) > MAX_DELTA)
            .ifPresent(previous -> report.warn(String.format(Locale.US,
                "Cambio brusco: %.2f -> %.2f en menos de 15 minutos. Repite la medición para confirmarlo.",
                previous.getPh(), context.ph())));
    }
}
