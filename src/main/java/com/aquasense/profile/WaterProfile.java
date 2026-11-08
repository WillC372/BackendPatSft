package com.aquasense.profile;

import com.aquasense.common.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;

/**
 * Perfil de agua con rangos de referencia.
 * Prototype: {@link #copyFor(Long, String)} clona un perfil existente (por ejemplo "Piscina")
 * para que el usuario lo personalice sin partir de cero ni modificar el original.
 */
@Entity
@Table(name = "water_profiles")
public class WaterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id")
    private Long ownerId;

    private String name;

    @Enumerated(EnumType.STRING)
    private ProfileType type;

    @Column(name = "ideal_min")
    private double idealMin;

    @Column(name = "ideal_max")
    private double idealMax;

    @Column(name = "caution_min")
    private double cautionMin;

    @Column(name = "caution_max")
    private double cautionMax;

    private String description;
    private String source;

    @Column(name = "system_profile")
    private boolean systemProfile;

    @Column(name = "created_at")
    private Instant createdAt;

    protected WaterProfile() {
    }

    public WaterProfile(Long ownerId, String name, ProfileType type, PhRange ranges, String description, String source) {
        this.ownerId = ownerId;
        this.name = name;
        this.type = type;
        this.description = description;
        this.source = source;
        this.systemProfile = false;
        this.createdAt = Instant.now();
        applyRanges(ranges);
    }

    public WaterProfile copyFor(Long newOwnerId, String newName) {
        String copiedSource = "Copia de \"" + name + "\". Fuente original: " + source;
        if (copiedSource.length() > 300) {
            copiedSource = copiedSource.substring(0, 300);
        }
        return new WaterProfile(newOwnerId, newName, type, ranges(), description, copiedSource);
    }

    public void update(String newName, PhRange ranges, String newDescription, String newSource) {
        if (systemProfile) {
            throw BusinessException.badRequest("Los perfiles del sistema no se pueden editar. Duplícalo para personalizarlo.");
        }
        this.name = newName;
        this.description = newDescription;
        this.source = newSource;
        applyRanges(ranges);
    }

    private void applyRanges(PhRange ranges) {
        ranges.validate();
        this.idealMin = ranges.idealMin();
        this.idealMax = ranges.idealMax();
        this.cautionMin = ranges.cautionMin();
        this.cautionMax = ranges.cautionMax();
    }

    public PhRange ranges() {
        return new PhRange(idealMin, idealMax, cautionMin, cautionMax);
    }

    public boolean isVisibleTo(Long userId) {
        return systemProfile || userId.equals(ownerId);
    }

    public String rangeLabel() {
        return String.format(Locale.US, "%.1f - %.1f", idealMin, idealMax);
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public ProfileType getType() { return type; }
    public double getIdealMin() { return idealMin; }
    public double getIdealMax() { return idealMax; }
    public double getCautionMin() { return cautionMin; }
    public double getCautionMax() { return cautionMax; }
    public String getDescription() { return description; }
    public String getSource() { return source; }
    public boolean isSystemProfile() { return systemProfile; }
}
