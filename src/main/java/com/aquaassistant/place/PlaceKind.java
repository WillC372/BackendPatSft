package com.aquaassistant.place;

/** What kind of water a place holds; it decides the suggested profile and the guide ranges of other parameters. */
public enum PlaceKind {
    GENERAL("Uso general"),
    POOL("Piscina"),
    SPA("Jacuzzi o spa"),
    AQUARIUM("Acuario"),
    POND("Estanque"),
    DRINKING("Agua potable"),
    IRRIGATION("Riego"),
    HYDROPONICS("Hidroponía"),
    OTHER("Otro");

    private final String label;

    PlaceKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
