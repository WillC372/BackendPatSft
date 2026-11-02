package com.aquasense.measurement.validation;

import java.util.ArrayList;
import java.util.List;

public final class ValidationOutcome {

    private final List<String> notes = new ArrayList<>();
    private boolean rejected;

    public void reject(String reason) {
        rejected = true;
        notes.add(reason);
    }

    public void flagSuspect(String reason) {
        notes.add(reason);
    }

    public boolean isRejected() {
        return rejected;
    }

    public boolean isSuspect() {
        return !rejected && !notes.isEmpty();
    }

    public List<String> getNotes() {
        return List.copyOf(notes);
    }
}
