package com.aquasense.measurement.validation;

/**
 * Chain of Responsibility: every link checks one aspect of the reading and passes control to the next one.
 * A link can reject the reading (the chain stops) or just add a note.
 */
public abstract class ReadingValidator {

    private ReadingValidator next;

    public ReadingValidator linkWith(ReadingValidator nextValidator) {
        this.next = nextValidator;
        return nextValidator;
    }

    public final void validate(ReadingContext context, ValidationReport report) {
        check(context, report);
        if (!report.isRejected() && next != null) {
            next.validate(context, report);
        }
    }

    protected abstract void check(ReadingContext context, ValidationReport report);
}
