package com.aquasense.vision;

public enum PhotoIssue {
    BLURRY("Foto desenfocada", true),
    LOW_LIGHT("Poca luz", true),
    GLARE("Reflejos", true),
    COLOR_CAST("Iluminación que altera los colores", true),
    NO_REFERENCE_CHART("La carta de colores no aparece en la foto", false),
    STRIP_NOT_VISIBLE("La tira no se ve con claridad", true),
    DISPLAY_NOT_READABLE("La pantalla no se puede leer", true),
    NOT_A_PH_TEST("La imagen no es una prueba de pH", false),
    AMBIGUOUS_COLOR("Color intermedio entre dos valores", true);

    private final String label;
    private final boolean lowersConfidence;

    PhotoIssue(String label, boolean lowersConfidence) {
        this.label = label;
        this.lowersConfidence = lowersConfidence;
    }

    public String label() {
        return label;
    }

    public boolean lowersConfidence() {
        return lowersConfidence;
    }
}
