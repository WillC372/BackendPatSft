package com.aquasense.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Un intento de pago de Premium. No guarda datos de tarjeta: solo la referencia y el resultado que informa PayU. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String provider;

    @Column(name = "reference_code")
    private String referenceCode;

    private BigDecimal amount;
    private String currency;

    @Column(name = "period_days")
    private int periodDays;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "test_mode")
    private boolean testMode;

    @Column(name = "provider_order_id")
    private String providerOrderId;

    @Column(name = "provider_tx_id")
    private String providerTxId;

    @Column(name = "provider_state")
    private String providerState;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "premium_until")
    private Instant premiumUntil;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(Long userId, String provider, String referenceCode, BigDecimal amount, String currency, int periodDays,
                   boolean testMode) {
        this.userId = userId;
        this.provider = provider;
        this.referenceCode = referenceCode;
        this.amount = amount;
        this.currency = currency;
        this.periodDays = periodDays;
        this.testMode = testMode;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    /** Aplica el resultado consultado en la pasarela. Un pago aprobado ya no cambia de estado. */
    public void update(PaymentStatus newStatus, String orderId, String txId, String state, String method) {
        if (status == PaymentStatus.APPROVED) {
            return;
        }
        this.status = newStatus;
        this.providerOrderId = orderId;
        this.providerTxId = txId;
        this.providerState = state;
        this.paymentMethod = method;
        this.updatedAt = Instant.now();
    }

    public void markApplied(Instant until) {
        this.premiumUntil = until;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        if (status == PaymentStatus.PENDING) {
            this.status = PaymentStatus.EXPIRED;
            this.updatedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getReferenceCode() { return referenceCode; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public int getPeriodDays() { return periodDays; }
    public PaymentStatus getStatus() { return status; }
    public boolean isTestMode() { return testMode; }
    public String getProviderOrderId() { return providerOrderId; }
    public String getProviderTxId() { return providerTxId; }
    public String getProviderState() { return providerState; }
    public String getPaymentMethod() { return paymentMethod; }
    public Instant getPremiumUntil() { return premiumUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
