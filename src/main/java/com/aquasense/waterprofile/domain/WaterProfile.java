package com.aquasense.waterprofile.domain;

import com.aquasense.shared.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "water_profiles")
public class WaterProfile {

    private static final BigDecimal MIN_PH = BigDecimal.ZERO;
    private static final BigDecimal MAX_PH = new BigDecimal("14");

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaterType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "optimal_min", nullable = false)
    private BigDecimal optimalMin;

    @Column(name = "optimal_max", nullable = false)
    private BigDecimal optimalMax;

    @Column(name = "caution_min", nullable = false)
    private BigDecimal cautionMin;

    @Column(name = "caution_max", nullable = false)
    private BigDecimal cautionMax;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WaterProfile() {
    }

    public WaterProfile(UUID ownerId, WaterType type, String name, String description, BigDecimal optimalMin,
                        BigDecimal optimalMax, BigDecimal cautionMin, BigDecimal cautionMax, String sourceReference) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.type = type;
        this.sourceReference = sourceReference;
        update(name, description, optimalMin, optimalMax, cautionMin, cautionMax);
    }

    public void update(String name, String description, BigDecimal optimalMin, BigDecimal optimalMax,
                       BigDecimal cautionMin, BigDecimal cautionMax) {
        boolean valid = cautionMin.compareTo(MIN_PH) >= 0 && cautionMax.compareTo(MAX_PH) <= 0
                && cautionMin.compareTo(optimalMin) <= 0 && optimalMin.compareTo(optimalMax) < 0
                && optimalMax.compareTo(cautionMax) <= 0;
        if (!valid) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "INVALID_RANGES",
                    "Los rangos deben cumplir 0 ≤ precaución mín ≤ óptimo mín < óptimo máx ≤ precaución máx ≤ 14.");
        }
        this.name = name;
        this.description = description;
        this.optimalMin = optimalMin;
        this.optimalMax = optimalMax;
        this.cautionMin = cautionMin;
        this.cautionMax = cautionMax;
    }

    public WaterProfile copyFor(UUID newOwnerId, String newName) {
        return new WaterProfile(newOwnerId, WaterType.CUSTOM, newName, description, optimalMin, optimalMax,
                cautionMin, cautionMax, sourceReference);
    }

    public PhClassification classify(BigDecimal ph) {
        if (ph.compareTo(MIN_PH) < 0 || ph.compareTo(MAX_PH) > 0) {
            throw new DomainException(HttpStatus.BAD_REQUEST, "PH_OUT_OF_SCALE", "El pH debe estar entre 0 y 14.");
        }
        PhRangeSpecification optimal = new PhRangeSpecification(optimalMin, optimalMax);
        PhRangeSpecification caution = new PhRangeSpecification(cautionMin, cautionMax);
        RangeStatus status = optimal.isSatisfiedBy(ph) ? RangeStatus.OPTIMAL
                : caution.isSatisfiedBy(ph) ? RangeStatus.CAUTION : RangeStatus.CRITICAL;
        return new PhClassification(ph, AcidityClass.of(ph), status, status == RangeStatus.OPTIMAL);
    }

    public boolean isSystem() {
        return ownerId == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public WaterType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getOptimalMin() {
        return optimalMin;
    }

    public BigDecimal getOptimalMax() {
        return optimalMax;
    }

    public BigDecimal getCautionMin() {
        return cautionMin;
    }

    public BigDecimal getCautionMax() {
        return cautionMax;
    }

    public String getSourceReference() {
        return sourceReference;
    }
}
