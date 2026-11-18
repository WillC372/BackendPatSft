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

/** Someone who joined another person's place with its code. */
@Entity
@Table(name = "place_members")
public class PlaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    private PlaceRole role;

    @Column(name = "joined_at")
    private Instant joinedAt;

    protected PlaceMember() {
    }

    public PlaceMember(Long placeId, Long userId, PlaceRole role) {
        this.placeId = placeId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public void changeRole(PlaceRole role) {
        this.role = role;
    }

    public Long getId() { return id; }
    public Long getPlaceId() { return placeId; }
    public Long getUserId() { return userId; }
    public PlaceRole getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
}
