package com.aquaassistant.reminder;

/** What the reminder is about; it gives the default title. */
public enum ReminderKind {
    MEASURE("Mide el pH hoy"),
    CLEAN_FILTER("Limpia el filtro"),
    CHECK_CHLORINE("Revisa el cloro"),
    WATER_CHANGE("Cambia parte del agua"),
    CALIBRATE("Calibra el medidor"),
    CUSTOM("Recordatorio");

    private final String defaultTitle;

    ReminderKind(String defaultTitle) {
        this.defaultTitle = defaultTitle;
    }

    public String defaultTitle() {
        return defaultTitle;
    }
}
