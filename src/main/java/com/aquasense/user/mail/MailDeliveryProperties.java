package com.aquasense.user.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del envío de correo. {@code provider}: auto (Mailjet, Brevo o SMTP, el primero configurado), mailjet, brevo, smtp o none.
 */
@ConfigurationProperties(prefix = "aquasense.mail-delivery")
public record MailDeliveryProperties(String provider, String senderName, Brevo brevo, Mailjet mailjet) {

    public MailDeliveryProperties {
        provider = provider == null || provider.isBlank() ? "auto" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        senderName = senderName == null || senderName.isBlank() ? "AquaSense AI" : senderName;
        brevo = brevo == null ? new Brevo(null, null, 0) : brevo;
        mailjet = mailjet == null ? new Mailjet(null, null, null, 0) : mailjet;
    }

    /** Mailjet Send API v3.1 con autenticación básica: llave de API como usuario y llave secreta como contraseña. */
    public record Mailjet(String apiKey, String secretKey, String baseUrl, int timeoutSeconds) {

        public Mailjet {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.mailjet.com" : baseUrl.replaceAll("/$", "");
            timeoutSeconds = timeoutSeconds <= 0 ? 15 : timeoutSeconds;
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank() && secretKey != null && !secretKey.isBlank();
        }
    }

    public record Brevo(String apiKey, String baseUrl, int timeoutSeconds) {

        public Brevo {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.brevo.com/v3" : baseUrl.replaceAll("/$", "");
            timeoutSeconds = timeoutSeconds <= 0 ? 15 : timeoutSeconds;
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
