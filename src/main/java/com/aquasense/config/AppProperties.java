package com.aquasense.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration read from application.yml and environment variables.
 * Spring creates it only once (singleton managed by the container).
 */
@ConfigurationProperties(prefix = "aquasense")
public record AppProperties(Security security, Cors cors, Mail mail, Ai ai, Device device) {

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

    public record Ai(String apiKey, String model, String effort, long maxTokens,
                     int dailyQuotaFree, int dailyQuotaPremium, int cacheMinutes, List<Provider> providers) {

        public Ai {
            providers = providers == null ? List.of() : List.copyOf(providers);
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * Provider with an OpenAI-compatible API (Gemini, Groq or another). The list order is the fallback order.
     */
    public record Provider(String id, String label, String baseUrl, String apiKey, String model, int timeoutSeconds,
                           boolean vision, boolean tools) {

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
        }
    }

    public record Device(long sampleIntervalMs, int measureSamples) {
    }
}
