package com.aquasense.measurement.validation;

import java.math.BigDecimal;
import java.time.Duration;

public class AbruptChangeHandler extends ValidationHandler {

    static final BigDecimal MAX_JUMP = new BigDecimal("2.0");
    static final Duration WINDOW = Duration.ofMinutes(10);

    @Override
    protected void check(ValidationContext context, ValidationOutcome outcome) {
        context.previous().ifPresent(prev -> {
            Duration gap = Duration.between(prev.measuredAt(), context.measuredAt()).abs();
            boolean closeInTime = gap.compareTo(WINDOW) <= 0;
            boolean bigJump = context.ph().subtract(prev.ph()).abs().compareTo(MAX_JUMP) > 0;
            if (closeInTime && bigJump) {
                outcome.flagSuspect("Cambio de más de 2 unidades de pH en menos de 10 minutos: puede ser un error "
                        + "del sensor o una calibración pendiente.");
            }
        });
    }
}
