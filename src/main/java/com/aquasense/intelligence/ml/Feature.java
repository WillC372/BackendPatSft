package com.aquasense.intelligence.ml;

/**
 * Variables que describen cada medición para el modelo. La hora se codifica como seno y coseno
 * para que las 23:00 y las 00:00 queden cerca; en las explicaciones ambas se reportan como "hora del día".
 */
public enum Feature {
    PH("Valor de pH", "pH"),
    DELTA("Cambio frente a la medición anterior", "pH"),
    RATE("Velocidad de cambio", "pH/h"),
    DEVIATION("Distancia a la mediana de las 10 mediciones previas", "pH"),
    HOUR_SIN("Hora del día", ""),
    HOUR_COS("Hora del día", ""),
    TEMPERATURE("Temperatura", "°C");

    private final String label;
    private final String unit;

    Feature(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }
}
