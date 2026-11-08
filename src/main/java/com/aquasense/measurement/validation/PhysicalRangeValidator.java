package com.aquasense.measurement.validation;

import com.aquasense.measurement.MeasurementSource;

public class PhysicalRangeValidator extends ReadingValidator {

    private static final double PROBE_FAULT_LOW = 0.5;
    private static final double PROBE_FAULT_HIGH = 13.5;

    @Override
    protected void check(ReadingContext context, ValidationReport report) {
        double ph = context.ph();
        if (Double.isNaN(ph) || ph < 0 || ph > 14) {
            report.reject("El valor de pH debe estar entre 0 y 14.");
            return;
        }
        if (context.source() == MeasurementSource.REAL && (ph < PROBE_FAULT_LOW || ph > PROBE_FAULT_HIGH)) {
            report.warn("Valor extremo: revisa que la sonda este sumergida y conectada.");
        }
    }
}
