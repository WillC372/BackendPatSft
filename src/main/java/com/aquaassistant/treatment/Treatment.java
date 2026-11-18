package com.aquaassistant.treatment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Something applied to the water of a place (a chemical, a water change, a filter cleaning). */
@Entity
@Table(name = "treatments")
public class Treatment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Enumerated(EnumType.STRING)
    private TreatmentProduct product;

    private Double amount;

    private String unit;

    private String note;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Treatment() {
    }

    public Treatment(Long placeId, Long userId, Instant appliedAt, TreatmentProduct product, Double amount, String unit, String note) {
        this.placeId = placeId;
        this.userId = userId;
        this.appliedAt = appliedAt;
        this.product = product;
        this.amount = amount;
        this.unit = unit;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPlaceId() { return placeId; }
    public Long getUserId() { return userId; }
    public Instant getAppliedAt() { return appliedAt; }
    public TreatmentProduct getProduct() { return product; }
    public Double getAmount() { return amount; }
    public String getUnit() { return unit; }
    public String getNote() { return note; }
}
