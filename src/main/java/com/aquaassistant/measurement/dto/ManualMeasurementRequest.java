package com.aquaassistant.measurement.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ManualMeasurementRequest(
    @NotNull(message = "Escribe el valor de pH.")
    @DecimalMin(value = "0.0", message = "El pH mínimo es 0.") @DecimalMax(value = "14.0", message = "El pH máximo es 14.")
    Double ph,
    @DecimalMin(value = "-10.0") @DecimalMax(value = "100.0") Double temperatureC,
    Instant measuredAt,
    @Size(max = 300, message = "Máximo 300 caracteres.") String note,
    @DecimalMin(value = "0.0", message = "El cloro no puede ser negativo.") @DecimalMax(value = "20.0", message = "El cloro libre máximo es 20 mg/L.") Double freeChlorine,
    @DecimalMin(value = "0.0", message = "La alcalinidad no puede ser negativa.") @DecimalMax(value = "500.0", message = "La alcalinidad máxima es 500 mg/L.") Double totalAlkalinity,
    @DecimalMin(value = "0.0", message = "La dureza no puede ser negativa.") @DecimalMax(value = "2000.0", message = "La dureza máxima es 2000 mg/L.") Double calciumHardness,
    @DecimalMin(value = "0.0", message = "El ácido cianúrico no puede ser negativo.") @DecimalMax(value = "300.0", message = "El ácido cianúrico máximo es 300 mg/L.") Double cyanuricAcid) {

    public ManualMeasurementRequest(Double ph, Double temperatureC, Instant measuredAt, String note) {
        this(ph, temperatureC, measuredAt, note, null, null, null, null);
    }

    public com.aquaassistant.measurement.WaterParams params() {
        return new com.aquaassistant.measurement.WaterParams(freeChlorine, totalAlkalinity, calciumHardness, cyanuricAcid);
    }
}
