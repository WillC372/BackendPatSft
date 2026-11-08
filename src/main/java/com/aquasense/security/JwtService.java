package com.aquasense.security;

import com.aquasense.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and validates JWT tokens signed with HS256.
 * The "ver" claim stores the user's token version: on logout the version
 * increases and all previous tokens stop being valid.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey key;
    private final AppProperties.Security config;

    public JwtService(AppProperties properties) {
        this.config = properties.security();
        String secret = config.jwtSecret();
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET must be set and contain at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(Long userId, String email, String role, int tokenVersion, boolean rememberMe) {
        Duration lifetime = rememberMe ? Duration.ofDays(config.rememberMeDays()) : Duration.ofHours(config.accessTokenHours());
        Instant now = Instant.now();
        Instant expiresAt = now.plus(lifetime);
        String token = Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("email", email)
            .claim("role", role)
            .claim("ver", tokenVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact();
        return new IssuedToken(token, expiresAt);
    }

    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new TokenClaims(Long.valueOf(claims.getSubject()), claims.get("email", String.class),
                claims.get("role", String.class), claims.get("ver", Integer.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public record TokenClaims(Long userId, String email, String role, Integer version) {
    }
}
