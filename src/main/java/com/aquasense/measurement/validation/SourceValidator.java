package com.aquasense.measurement.validation;

import com.aquasense.measurement.MeasurementSource;

public class SourceValidator extends ReadingValidator {

    @Override
    protected void check(ReadingContext context, ValidationReport report) {
        if (context.source() == MeasurementSource.SIMULATED) {
            report.warn("Lectura generada por el simulador: no corresponde a agua real.");
        } else if (context.source() == MeasurementSource.PHOTO) {
            report.warn("Valor estimado por IA a partir de una foto; confírmalo con un medidor calibrado cuando sea posible.");
        }
    }
}
