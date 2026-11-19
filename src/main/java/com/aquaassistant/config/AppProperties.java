package com.aquaassistant.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration read from application.yml and environment variables.
 * Spring creates it only once (singleton managed by the container).
 */
@ConfigurationProperties(prefix = "aquasense")
public record AppProperties(Security security, Cors cors, Mail mail, Device device) {

    public record Security(String jwtSecret, int accessTokenHours, int rememberMeDays, String adminEmail,
                           int rateLimitPerMinute) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * {@code logResetLinks}: development only. Without SMTP, it allows writing the recovery link to the log.
     * In production it must stay false (the default): without SMTP, recovery responds that it is not available.
     */
    public record Mail(String from, String frontendResetUrl, boolean logResetLinks, String frontendAdminVerifyUrl) {

        public Mail {
            if (frontendAdminVerifyUrl == null || frontendAdminVerifyUrl.isBlank()) {
                frontendAdminVerifyUrl = frontendResetUrl == null ? null : frontendResetUrl.replace("reset-password", "verify-admin");
            }
        }
    }

    /**
     * {@code simulatorEnabled}: offers the simulated meter. Off by default so a deployed app never shows fake readings;
     * the automated tests turn it on.
     */
    public record Device(long sampleIntervalMs, int measureSamples, boolean simulatorEnabled) {
    }
}
