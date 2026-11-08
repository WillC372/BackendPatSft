package com.aquasense.intelligence.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_reports")
public class AgentReportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String source;
    private String provider;
    private String model;

    @Column(name = "period_days")
    private int periodDays;

    private String title;
    private String payload;

    @Column(name = "created_at")
    private Instant createdAt;

    protected AgentReportRecord() {
    }

    public AgentReportRecord(Long userId, String source, String provider, String model, int periodDays, String title,
                             String payload, Instant createdAt) {
        this.userId = userId;
        this.source = source;
        this.provider = provider;
        this.model = model;
        this.periodDays = periodDays;
        this.title = title.length() > 300 ? title.substring(0, 300) : title;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getSource() { return source; }
    public String getProvider() { return provider; }
    public int getPeriodDays() { return periodDays; }
    public String getTitle() { return title; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
