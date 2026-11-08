package com.aquasense.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(@NotBlank(message = "Falta el código del enlace.") @Size(max = 200) String token) {
}
