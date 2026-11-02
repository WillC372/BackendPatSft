package com.aquasense.measurement.validation;

public abstract class ValidationHandler {

    private ValidationHandler next;

    public ValidationHandler linkWith(ValidationHandler nextHandler) {
        this.next = nextHandler;
        return nextHandler;
    }

    public final void handle(ValidationContext context, ValidationOutcome outcome) {
        check(context, outcome);
        if (!outcome.isRejected() && next != null) {
            next.handle(context, outcome);
        }
    }

    protected abstract void check(ValidationContext context, ValidationOutcome outcome);
}
