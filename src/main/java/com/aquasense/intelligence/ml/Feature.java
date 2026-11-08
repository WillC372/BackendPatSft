package com.aquasense.intelligence.ml;

/**
 * Variables that describe each measurement for the model. The hour is encoded as sine and cosine
 * so 23:00 and 00:00 stay close; in the explanations both are reported as "time of day".
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
