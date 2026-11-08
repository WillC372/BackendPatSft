package com.aquasense.intelligence.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ScenarioRequest(
    @NotNull(message = "Indica el pH hipotético.") @DecimalMin("0.0") @DecimalMax("14.0") Double hypotheticalPh) {
}
