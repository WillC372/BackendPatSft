package com.aquasense.security;

/** {@code sessionId}: session of the login that issued the token (see {@code UserSession}). */
public record AuthenticatedUser(Long id, String email, String role, Long sessionId) {
}
