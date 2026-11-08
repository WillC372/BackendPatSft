package com.aquasense.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Escribe tu nombre.") @Size(max = 120, message = "Máximo 120 caracteres.") String fullName,
    @NotBlank(message = "Escribe tu correo.") @Email(message = "El correo no es válido.") @Size(max = 160) String email,
    @NotBlank(message = "Escribe una contraseña.")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Usa al menos una letra y un número.")
    String password) {
}
