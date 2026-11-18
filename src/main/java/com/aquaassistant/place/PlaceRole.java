package com.aquaassistant.place;

/** What a person can do in a place: the owner manages it, an editor also measures and records, a viewer only looks. */
public enum PlaceRole {
    OWNER("Dueño"),
    EDITOR("Puede medir"),
    VIEWER("Solo ver");

    private final String label;

    PlaceRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canEdit() {
        return this != VIEWER;
    }
}
