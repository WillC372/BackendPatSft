package com.aquasense.profile.dto;

import com.aquasense.profile.PhRange;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
    @NotBlank(message = "Escribe un nombre.") @Size(max = 80) String name,
    @NotNull @DecimalMin("0.0") @DecimalMax("14.0") Double idealMin,
    @NotNull @DecimalMin("0.0") @DecimalMax("14.0") Double idealMax,
    @NotNull @DecimalMin("0.0") @DecimalMax("14.0") Double cautionMin,
    @NotNull @DecimalMin("0.0") @DecimalMax("14.0") Double cautionMax,
    @NotBlank(message = "Describe el uso del perfil.") @Size(max = 500) String description,
    @NotBlank(message = "Indica la fuente del rango.") @Size(max = 300) String source) {

    public PhRange toRange() {
        return new PhRange(idealMin, idealMax, cautionMin, cautionMax);
    }
}
