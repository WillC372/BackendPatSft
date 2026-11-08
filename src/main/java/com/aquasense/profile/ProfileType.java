package com.aquasense.profile;

public enum ProfileType {
    GENERAL("Uso general"),
    DRINKING("Agua potable"),
    POOL("Piscina"),
    AQUARIUM("Acuario"),
    IRRIGATION("Agricultura y riego"),
    CUSTOM("Personalizado");

    private final String label;

    ProfileType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
