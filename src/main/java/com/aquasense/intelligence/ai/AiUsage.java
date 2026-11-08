package com.aquasense.intelligence.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una llamada a la IA: tokens, proveedor, latencia, si vino de la caché, si falló y su costo estimado.
 * Los intentos fallidos se guardan con {@code success = false} y no cuentan para la cuota diaria.
 */
@Entity
@Table(name = "ai_usage")
public class AiUsage {

    private static final int MAX_ERROR = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String purpose;
    private String provider;
    private String model;

    @Column(name = "input_tokens")
    private long inputTokens;

    @Column(name = "output_tokens")
    private long outputTokens;

    private boolean cached;
    private boolean success = true;
    private String error;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "cost_usd")
    private BigDecimal costUsd = BigDecimal.ZERO;

    @Column(name = "created_at")
    private Instant createdAt;

    protected AiUsage() {
    }

    private AiUsage(Long userId, String purpose, String provider, String model, long inputTokens, long outputTokens,
                    boolean cached, boolean success, String error, Long latencyMs, BigDecimal costUsd) {
        this.userId = userId;
        this.purpose = purpose;
        this.provider = cut(provider, 60);
        this.model = cut(model == null ? "desconocido" : model, 60);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cached = cached;
        this.success = success;
        this.error = cut(error, MAX_ERROR);
        this.latencyMs = latencyMs;
        this.costUsd = costUsd == null ? BigDecimal.ZERO : costUsd;
        this.createdAt = Instant.now();
    }

    public static AiUsage success(Long userId, String purpose, AiAnswer answer, long latencyMs, BigDecimal costUsd) {
        return new AiUsage(userId, purpose, answer.provider(), answer.model(), answer.inputTokens(), answer.outputTokens(),
            answer.cached(), true, null, latencyMs, answer.cached() ? BigDecimal.ZERO : costUsd);
    }

    public static AiUsage success(Long userId, String purpose, String provider, String model, long inputTokens,
                                  long outputTokens, long latencyMs, BigDecimal costUsd) {
        return new AiUsage(userId, purpose, provider, model, inputTokens, outputTokens, false, true, null, latencyMs, costUsd);
    }

    public static AiUsage failure(Long userId, String purpose, String provider, String model, long latencyMs, String error) {
        return new AiUsage(userId, purpose, provider, model, 0, 0, false, false, error, latencyMs, BigDecimal.ZERO);
    }

    private static String cut(String value, int max) {
        return value == null ? null : value.length() > max ? value.substring(0, max) : value;
    }

    public Long getUserId() { return userId; }
    public String getPurpose() { return purpose; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public boolean isCached() { return cached; }
    public boolean isSuccess() { return success; }
    public String getError() { return error; }
    public Long getLatencyMs() { return latencyMs; }
    public BigDecimal getCostUsd() { return costUsd; }
    public Instant getCreatedAt() { return createdAt; }
}
