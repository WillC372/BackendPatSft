package com.aquasense.measurement.validation;

/**
 * Chain of Responsibility: cada eslabon revisa un aspecto de la lectura y pasa el control al siguiente.
 * Un eslabon puede rechazar la lectura (se detiene la cadena) o solo agregar una observacion.
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
