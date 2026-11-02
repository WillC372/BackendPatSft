package com.aquasense.reporting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports")
public class ReportRecord {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportFormat format;

    @Column(name = "period_from")
    private Instant periodFrom;

    @Column(name = "period_to")
    private Instant periodTo;

    @Column(name = "measurement_count", nullable = false)
    private int measurementCount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ReportRecord() {
    }

    public ReportRecord(UUID ownerId, UUID profileId, String title, ReportFormat format, Instant from, Instant to,
                        int measurementCount, String content) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.profileId = profileId;
        this.title = title;
        this.format = format;
        this.periodFrom = from;
        this.periodTo = to;
        this.measurementCount = measurementCount;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getTitle() {
        return title;
    }

    public ReportFormat getFormat() {
        return format;
    }

    public Instant getPeriodFrom() {
        return periodFrom;
    }

    public Instant getPeriodTo() {
        return periodTo;
    }

    public int getMeasurementCount() {
        return measurementCount;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
