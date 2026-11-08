package com.aquasense.user.dto;

import java.time.Instant;

/** {@code notice}: aviso opcional para el usuario, por ejemplo que revise su correo para confirmar el rol de administrador. */
public record AuthResponse(String token, Instant expiresAt, UserResponse user, String notice) {

    public AuthResponse withNotice(String text) {
        return new AuthResponse(token, expiresAt, user, text);
    }
}
