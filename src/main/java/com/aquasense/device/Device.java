package com.aquasense.device;

import com.aquasense.device.hardware.MeterProtocol;
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
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id")
    private Long ownerId;

    private String name;

    @Enumerated(EnumType.STRING)
    private MeterProtocol protocol;

    private String port;

    private boolean simulated;

    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Device() {
    }

    public Device(Long ownerId, String name, MeterProtocol protocol, String port, boolean simulated) {
        this.ownerId = ownerId;
        this.name = name;
        this.protocol = protocol;
        this.port = port;
        this.simulated = simulated;
        this.createdAt = Instant.now();
    }

    public void markConnected(String newName, MeterProtocol newProtocol) {
        this.name = newName;
        this.protocol = newProtocol;
        this.lastConnectedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public MeterProtocol getProtocol() { return protocol; }
    public String getPort() { return port; }
    public boolean isSimulated() { return simulated; }
    public Instant getLastConnectedAt() { return lastConnectedAt; }
}
