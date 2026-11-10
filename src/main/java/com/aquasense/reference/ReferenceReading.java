package com.aquasense.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One official pH reading of a station. */
@Entity
@Table(name = "reference_readings")
public class ReferenceReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private String stationId;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(nullable = false)
    private BigDecimal ph;

    @Column(name = "approval_status")
    private String approvalStatus;

    protected ReferenceReading() {
    }

    public ReferenceReading(String stationId, Instant measuredAt, BigDecimal ph, String approvalStatus) {
        this.stationId = stationId;
        this.measuredAt = measuredAt;
        this.ph = ph;
        this.approvalStatus = approvalStatus;
    }

    public Long getId() { return id; }
    public String getStationId() { return stationId; }
    public Instant getMeasuredAt() { return measuredAt; }
    public BigDecimal getPh() { return ph; }
    public String getApprovalStatus() { return approvalStatus; }
}
