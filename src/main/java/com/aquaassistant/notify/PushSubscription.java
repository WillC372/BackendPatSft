package com.aquaassistant.notify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A browser or phone where the person accepted notifications (its push service address and keys). */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private String endpoint;

    private String p256dh;

    private String auth;

    @Column(name = "device_label")
    private String deviceLabel;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    protected PushSubscription() {
    }

    public PushSubscription(Long userId, String endpoint, String p256dh, String auth, String deviceLabel) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.deviceLabel = deviceLabel;
        this.createdAt = Instant.now();
    }

    public void renew(Long userId, String p256dh, String auth, String deviceLabel) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.deviceLabel = deviceLabel;
    }

    public void sent() {
        this.lastSentAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public String getDeviceLabel() { return deviceLabel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSentAt() { return lastSentAt; }
}
