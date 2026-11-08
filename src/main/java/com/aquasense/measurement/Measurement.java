package com.aquasense.measurement;

import com.aquasense.measurement.validation.ValidationStatus;
import com.aquasense.profile.PhNature;
import com.aquasense.profile.PhStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "measurements")
public class Measurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    private double ph;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "measured_at")
    private Instant measuredAt;

    @Enumerated(EnumType.STRING)
    private MeasurementSource source;

    @Enumerated(EnumType.STRING)
    private PhNature nature;

    @Enumerated(EnumType.STRING)
    private PhStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status")
    private ValidationStatus validationStatus;

    @Column(name = "validation_notes")
    private String validationNotes;

    private String note;

    protected Measurement() {
    }

    public Measurement(Long userId, Long deviceId, Long profileId, double ph, Double temperatureC, Instant measuredAt,
                       MeasurementSource source, PhNature nature, PhStatus status, ValidationStatus validationStatus,
                       String validationNotes, String note) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.profileId = profileId;
        this.ph = Math.round(ph * 100.0) / 100.0;
        this.temperatureC = temperatureC;
        this.measuredAt = measuredAt;
        this.source = source;
        this.nature = nature;
        this.status = status;
        this.validationStatus = validationStatus;
        this.validationNotes = validationNotes;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getDeviceId() { return deviceId; }
    public Long getProfileId() { return profileId; }
    public double getPh() { return ph; }
    public Double getTemperatureC() { return temperatureC; }
    public Instant getMeasuredAt() { return measuredAt; }
    public MeasurementSource getSource() { return source; }
    public PhNature getNature() { return nature; }
    public PhStatus getStatus() { return status; }
    public ValidationStatus getValidationStatus() { return validationStatus; }
    public String getValidationNotes() { return validationNotes; }
    public String getNote() { return note; }
}
