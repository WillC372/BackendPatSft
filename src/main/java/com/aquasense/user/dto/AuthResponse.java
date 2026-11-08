package com.aquasense.user.dto;

import java.time.Instant;

/** {@code notice}: optional notice for the user, for example to check their email to confirm the administrator role. */
public record AuthResponse(String token, Instant expiresAt, UserResponse user, String notice) {

    public AuthResponse withNotice(String text) {
        return new AuthResponse(token, expiresAt, user, text);
    }
}
