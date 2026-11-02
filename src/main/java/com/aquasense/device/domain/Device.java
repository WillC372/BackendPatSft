package com.aquasense.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    private String manufacturer;

    private String model;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false)
    private ConnectionType connectionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false)
    private SourceKind sourceKind;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Device() {
    }

    public Device(UUID ownerId, String name, String manufacturer, String model, String externalId,
                  ConnectionType connectionType) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name;
        this.manufacturer = manufacturer;
        this.model = model;
        this.externalId = externalId;
        this.connectionType = connectionType;
        this.sourceKind = connectionType == ConnectionType.SIMULATOR ? SourceKind.SIMULATED : SourceKind.REAL;
    }

    public void markSeen(Instant when) {
        this.lastSeenAt = when;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public String getExternalId() {
        return externalId;
    }

    public ConnectionType getConnectionType() {
        return connectionType;
    }

    public SourceKind getSourceKind() {
        return sourceKind;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
