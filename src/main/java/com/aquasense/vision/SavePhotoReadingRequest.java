package com.aquasense.vision;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record SavePhotoReadingRequest(
    @DecimalMin(value = "0.0", message = "El pH mínimo es 0.") @DecimalMax(value = "14.0", message = "El pH máximo es 14.") Double ph,
    @Size(max = 120, message = "Máximo 120 caracteres.") String note) {
}
