package com.aquasense.intelligence.domain;

import com.aquasense.intelligence.engine.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(nullable = false)
    private BigDecimal ph;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(nullable = false, length = 1000)
    private String explanation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    protected Alert() {
    }

    public Alert(UUID ownerId, UUID analysisId, String type, Severity severity, Instant measuredAt, BigDecimal ph,
                 String message, String explanation) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.analysisId = analysisId;
        this.type = type;
        this.severity = severity;
        this.measuredAt = measuredAt;
        this.ph = ph;
        this.message = message.length() > 300 ? message.substring(0, 300) : message;
        this.explanation = explanation.length() > 1000 ? explanation.substring(0, 1000) : explanation;
    }

    public void acknowledge(Instant when) {
        this.acknowledgedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getType() {
        return type;
    }

    public Severity getSeverity() {
        return severity;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public BigDecimal getPh() {
        return ph;
    }

    public String getMessage() {
        return message;
    }

    public String getExplanation() {
        return explanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }
}
