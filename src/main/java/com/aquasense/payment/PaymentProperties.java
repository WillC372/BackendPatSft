package com.aquasense.payment;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de pagos. Por defecto usa el sandbox de PayU con las credenciales públicas de prueba que PayU publica
 * en su documentación (comercio 508029, cuenta 512321 de Colombia): no mueve dinero real ni requiere registrar un comercio.
 */
@ConfigurationProperties(prefix = "aquasense.payments")
public record PaymentProperties(boolean enabled, BigDecimal price, String currency, int periodDays, int reminderDays,
                                String responseUrl, String confirmationUrl, Payu payu) {

    public PaymentProperties {
        price = price == null ? new BigDecimal("49900") : price;
        currency = currency == null || currency.isBlank() ? "COP" : currency;
        periodDays = periodDays <= 0 ? 30 : periodDays;
        reminderDays = reminderDays <= 0 ? 3 : reminderDays;
        payu = payu == null ? new Payu(null, null, null, null, null, null, true) : payu;
    }

    public record Payu(String merchantId, String accountId, String apiKey, String apiLogin, String checkoutUrl, String apiUrl,
                       boolean test) {

        public Payu {
            merchantId = blank(merchantId) ? "508029" : merchantId;
            accountId = blank(accountId) ? "512321" : accountId;
            apiKey = blank(apiKey) ? "4Vj8eK4rloUd272L48hsrarnUA" : apiKey;
            apiLogin = blank(apiLogin) ? "pRRXKOl8ikMmt9u" : apiLogin;
            checkoutUrl = blank(checkoutUrl) ? "https://sandbox.checkout.payulatam.com/ppp-web-gateway-payu/" : checkoutUrl;
            apiUrl = blank(apiUrl) ? "https://sandbox.api.payulatam.com" : apiUrl.replaceAll("/$", "");
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
