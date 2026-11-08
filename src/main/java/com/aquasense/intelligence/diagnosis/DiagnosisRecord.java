package com.aquasense.intelligence.diagnosis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_diagnoses")
public class DiagnosisRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String source;
    private String provider;
    private String model;

    @Column(name = "risk_level")
    private String riskLevel;

    private String payload;

    @Column(name = "created_at")
    private Instant createdAt;

    protected DiagnosisRecord() {
    }

    public DiagnosisRecord(Long userId, String source, String provider, String model, String riskLevel, String payload,
                           Instant createdAt) {
        this.userId = userId;
        this.source = source;
        this.provider = provider;
        this.model = model;
        this.riskLevel = riskLevel;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
