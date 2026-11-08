package com.aquasense.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank(message = "Escribe tu correo.") @Email(message = "El correo no es válido.") String email) {
}
