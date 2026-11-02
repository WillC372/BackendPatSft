package com.aquasense.measurement.validation;

import java.math.BigDecimal;

public class PhScaleHandler extends ValidationHandler {

    private static final BigDecimal MAX = new BigDecimal("14");

    @Override
    protected void check(ValidationContext context, ValidationOutcome outcome) {
        if (context.ph().signum() < 0 || context.ph().compareTo(MAX) > 0) {
            outcome.reject("El pH debe estar entre 0 y 14.");
        }
    }
}
