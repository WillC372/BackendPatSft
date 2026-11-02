package com.aquasense.measurement.domain;

import com.aquasense.device.domain.SourceKind;
import com.aquasense.waterprofile.domain.AcidityClass;
import com.aquasense.waterprofile.domain.RangeStatus;
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
@Table(name = "measurements")
public class Measurement {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(nullable = false)
    private BigDecimal ph;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false)
    private SourceKind sourceKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcidityClass acidity;

    @Enumerated(EnumType.STRING)
    @Column(name = "range_status", nullable = false)
    private RangeStatus rangeStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus;

    @Column(name = "validation_notes", length = 500)
    private String validationNotes;

    protected Measurement() {
    }

    public Measurement(UUID ownerId, UUID deviceId, UUID profileId, BigDecimal ph, Instant measuredAt,
                       SourceKind sourceKind, AcidityClass acidity, RangeStatus rangeStatus,
                       ValidationStatus validationStatus, String validationNotes) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.deviceId = deviceId;
        this.profileId = profileId;
        this.ph = ph;
        this.measuredAt = measuredAt;
        this.sourceKind = sourceKind;
        this.acidity = acidity;
        this.rangeStatus = rangeStatus;
        this.validationStatus = validationStatus;
        this.validationNotes = validationNotes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public BigDecimal getPh() {
        return ph;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public SourceKind getSourceKind() {
        return sourceKind;
    }

    public AcidityClass getAcidity() {
        return acidity;
    }

    public RangeStatus getRangeStatus() {
        return rangeStatus;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public String getValidationNotes() {
        return validationNotes;
    }
}
