package com.aquasense.intelligence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analyses")
public class AnalysisRecord {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "period_from")
    private Instant periodFrom;

    @Column(name = "period_to")
    private Instant periodTo;

    @Column(name = "measurement_count", nullable = false)
    private int measurementCount;

    @Column(name = "sufficient_data", nullable = false)
    private boolean sufficientData;

    @Column(name = "report_json", nullable = false, columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AnalysisRecord() {
    }

    public AnalysisRecord(UUID ownerId, UUID profileId, Instant periodFrom, Instant periodTo, int measurementCount,
                          boolean sufficientData, String reportJson) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.profileId = profileId;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.measurementCount = measurementCount;
        this.sufficientData = sufficientData;
        this.reportJson = reportJson;
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

    public Instant getPeriodFrom() {
        return periodFrom;
    }

    public Instant getPeriodTo() {
        return periodTo;
    }

    public int getMeasurementCount() {
        return measurementCount;
    }

    public boolean isSufficientData() {
        return sufficientData;
    }

    public String getReportJson() {
        return reportJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
