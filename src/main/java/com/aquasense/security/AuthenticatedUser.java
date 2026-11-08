package com.aquasense.security;

public record AuthenticatedUser(Long id, String email, String role) {
}
