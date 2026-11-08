package com.aquasense.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Contracts of the payments API. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PlanStatus(String plan, Instant premiumUntil, boolean manualPremium, boolean paymentsEnabled,
                             BigDecimal price, String currency, int periodDays, String provider, boolean testMode,
                             List<TestCase> testCases, String testNote) {
    }

    /** Test data published by PayU to get each result in the sandbox. */
    public record TestCase(String result, String cardNumber, String cardholder, String cvv, String expiry) {
    }

    public record CheckoutResponse(String actionUrl, Map<String, String> fields, String referenceCode, BigDecimal amount,
                                   String currency, boolean testMode) {
    }

    public record VerifyRequest(@NotBlank(message = "Falta la referencia del pago.") @Size(max = 64) String referenceCode) {
    }

    public record PaymentView(String referenceCode, BigDecimal amount, String currency, String status, String statusLabel,
                              String paymentMethod, boolean testMode, Instant createdAt, Instant updatedAt,
                              Instant premiumUntil, String message) {

        static PaymentView of(Payment p, String message) {
            return new PaymentView(p.getReferenceCode(), p.getAmount(), p.getCurrency(), p.getStatus().name(),
                p.getStatus().label(), p.getPaymentMethod(), p.isTestMode(), p.getCreatedAt(), p.getUpdatedAt(),
                p.getPremiumUntil(), message);
        }
    }
}
