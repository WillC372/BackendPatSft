package com.aquaassistant.treatment;

/** What was applied or done to the water. */
public enum TreatmentProduct {
    SODA_ASH("Carbonato de sodio (sube el pH)"),
    SODIUM_BICARBONATE("Bicarbonato de sodio (sube la alcalinidad)"),
    MURIATIC_ACID("Ácido muriático (baja el pH y la alcalinidad)"),
    SODIUM_BISULFATE("Bisulfato de sodio (baja el pH)"),
    LIQUID_CHLORINE("Cloro líquido (hipoclorito de sodio)"),
    CAL_HYPO("Hipoclorito de calcio (cloro granulado)"),
    DICHLOR("Dicloro (cloro estabilizado)"),
    TRICHLOR("Tricloro (pastillas)"),
    CALCIUM_CHLORIDE("Cloruro de calcio (sube la dureza)"),
    CYANURIC_ACID("Ácido cianúrico (estabilizador)"),
    WATER_CONDITIONER("Acondicionador de agua (acuario)"),
    PH_BUFFER("Regulador de pH (acuario o riego)"),
    WATER_CHANGE("Cambio de agua"),
    FILTER_CLEANING("Limpieza del filtro"),
    OTHER("Otro");

    private final String label;

    TreatmentProduct(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
