package com.aquasense.measurement.dto;

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
    @Size(max = 300, message = "Máximo 300 caracteres.") String note) {
}
