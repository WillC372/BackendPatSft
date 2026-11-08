package com.aquasense.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración tipada leida de application.yml y variables de entorno.
 * Spring la crea una sola vez (singleton gestionado por el contenedor).
 */
@ConfigurationProperties(prefix = "aquasense")
public record AppProperties(Security security, Cors cors, Mail mail, Ai ai, Device device) {

    public record Security(String jwtSecret, int accessTokenHours, int rememberMeDays, String adminEmail,
                           int rateLimitPerMinute) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * {@code logResetLinks}: solo para desarrollo. Si no hay SMTP, permite escribir el enlace de recuperación en el log.
     * En producción debe quedar en false (valor por defecto): sin SMTP la recuperación responde que no está disponible.
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
     * Proveedor con API compatible con OpenAI (Gemini, Groq u otro). El orden de la lista es el orden de respaldo.
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
