package com.aquaassistant.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A body of water the person follows: a pool, an aquarium, a tank... Measurements and treatments belong to one. */
@Entity
@Table(name = "places")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    private String name;

    @Enumerated(EnumType.STRING)
    private PlaceKind kind;

    @Column(name = "volume_liters")
    private Double volumeLiters;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "share_code")
    private String shareCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "share_role")
    private PlaceRole shareRole;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Place() {
    }

    public Place(Long ownerId, String name, PlaceKind kind, Double volumeLiters, Long profileId) {
        this.ownerId = ownerId;
        this.name = name;
        this.kind = kind;
        this.volumeLiters = volumeLiters;
        this.profileId = profileId;
        this.createdAt = Instant.now();
    }

    public void update(String name, PlaceKind kind, Double volumeLiters, Long profileId) {
        this.name = name;
        this.kind = kind;
        this.volumeLiters = volumeLiters;
        this.profileId = profileId;
    }

    public void useProfile(Long profileId) {
        this.profileId = profileId;
    }

    /** Opens the place to whoever has the code, with the given role (VIEWER or EDITOR). */
    public void share(String code, PlaceRole role) {
        this.shareCode = code;
        this.shareRole = role;
    }

    public void stopSharing() {
        this.shareCode = null;
        this.shareRole = null;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public PlaceKind getKind() { return kind; }
    public Double getVolumeLiters() { return volumeLiters; }
    public Long getProfileId() { return profileId; }
    public String getShareCode() { return shareCode; }
    public PlaceRole getShareRole() { return shareRole; }
    public Instant getCreatedAt() { return createdAt; }
}
