package com.aquasense.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Official monitoring station with its most recent pH value. */
@Entity
@Table(name = "reference_stations")
public class ReferenceStation {

    @Id
    private String id;

    @Column(nullable = false)
    private String agency;

    private String name;
    private String state;

    @Column(name = "site_type")
    private String siteType;

    private Double latitude;
    private Double longitude;

    @Column(name = "last_ph")
    private BigDecimal lastPh;

    @Column(name = "last_time")
    private Instant lastTime;

    @Column(name = "approval_status")
    private String approvalStatus;

    private boolean featured;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReferenceStation() {
    }

    public ReferenceStation(String id, String agency) {
        this.id = id;
        this.agency = agency;
        this.updatedAt = Instant.now();
    }

    /** Keeps the newest value: an older one (for example from a second time series of the station) does not replace it. */
    public void latest(BigDecimal ph, Instant time, String approval, Double latitude, Double longitude) {
        if (lastTime == null || !time.isBefore(lastTime)) {
            this.lastPh = ph;
            this.lastTime = time;
            this.approvalStatus = approval;
        }
        if (latitude != null) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
        this.updatedAt = Instant.now();
    }

    public void describe(String name, String state, String siteType) {
        this.name = name;
        this.state = state;
        this.siteType = siteType;
    }

    public void feature(boolean featured) {
        this.featured = featured;
    }

    public String getId() { return id; }
    public String getAgency() { return agency; }
    public String getName() { return name; }
    public String getState() { return state; }
    public String getSiteType() { return siteType; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public BigDecimal getLastPh() { return lastPh; }
    public Instant getLastTime() { return lastTime; }
    public String getApprovalStatus() { return approvalStatus; }
    public boolean isFeatured() { return featured; }
    public Instant getUpdatedAt() { return updatedAt; }
}
