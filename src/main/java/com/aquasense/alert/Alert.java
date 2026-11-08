package com.aquasense.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "measurement_id")
    private Long measurementId;

    private String severity;
    private String title;
    private String message;

    @Column(name = "is_read")
    private boolean read;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Alert() {
    }

    public Alert(Long userId, Long measurementId, String severity, String title, String message) {
        this.userId = userId;
        this.measurementId = measurementId;
        this.severity = severity;
        this.title = title;
        this.message = message.length() > 600 ? message.substring(0, 600) : message;
        this.createdAt = Instant.now();
    }

    public void markRead() {
        this.read = true;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getMeasurementId() { return measurementId; }
    public String getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }
}
