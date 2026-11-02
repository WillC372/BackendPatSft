package com.aquasense.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aquasense.security")
public record SecurityProperties(
        String jwtSecret,
        long accessTokenMinutes,
        long refreshTokenDays,
        long refreshTokenRememberDays,
        long resetTokenMinutes) {

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.getBytes().length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET debe definirse con al menos 32 bytes (variable de entorno JWT_SECRET).");
        }
    }
}
