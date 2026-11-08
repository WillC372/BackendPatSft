package com.aquasense.measurement.validation;

import org.springframework.stereotype.Component;

@Component
public class MeasurementValidationChain {

    private final ReadingValidator first;

    public MeasurementValidationChain() {
        first = new PhysicalRangeValidator();
        first.linkWith(new TimestampValidator())
            .linkWith(new TemperatureValidator())
            .linkWith(new SuddenChangeValidator())
            .linkWith(new SourceValidator());
    }

    public ValidationReport validate(ReadingContext context) {
        ValidationReport report = new ValidationReport();
        first.validate(context, report);
        return report;
    }
}
