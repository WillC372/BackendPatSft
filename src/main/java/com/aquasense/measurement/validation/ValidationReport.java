package com.aquasense.measurement.validation;

import java.util.ArrayList;
import java.util.List;

public class ValidationReport {

    private final List<String> warnings = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    public void warn(String message) {
        warnings.add(message);
    }

    public void reject(String message) {
        errors.add(message);
    }

    public boolean isRejected() {
        return !errors.isEmpty();
    }

    public ValidationStatus status() {
        if (isRejected()) {
            return ValidationStatus.REJECTED;
        }
        return warnings.isEmpty() ? ValidationStatus.VALID : ValidationStatus.WARNING;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public String notes() {
        List<String> all = new ArrayList<>(errors);
        all.addAll(warnings);
        String joined = String.join(" ", all);
        return joined.length() > 500 ? joined.substring(0, 500) : joined;
    }
}
