package com.aquasense.measurement.validation;

import org.springframework.stereotype.Component;

@Component
public class MeasurementValidationChain {

    private final ValidationHandler first;

    public MeasurementValidationChain() {
        PhScaleHandler scale = new PhScaleHandler();
        scale.linkWith(new FutureTimestampHandler())
                .linkWith(new StaleTimestampHandler())
                .linkWith(new AbruptChangeHandler());
        this.first = scale;
    }

    public ValidationOutcome validate(ValidationContext context) {
        ValidationOutcome outcome = new ValidationOutcome();
        first.handle(context, outcome);
        return outcome;
    }
}
