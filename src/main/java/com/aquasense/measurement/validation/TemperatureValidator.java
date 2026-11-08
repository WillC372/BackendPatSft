package com.aquasense.measurement.validation;

public class TemperatureValidator extends ReadingValidator {

    private static final double MIN_C = 0;
    private static final double MAX_C = 50;

    @Override
    protected void check(ReadingContext context, ValidationReport report) {
        Double temperature = context.temperatureC();
        if (temperature != null && (temperature < MIN_C || temperature > MAX_C)) {
            report.warn("Temperatura fuera de 0-50 °C: la compensación de pH puede no ser confiable.");
        }
    }
}
