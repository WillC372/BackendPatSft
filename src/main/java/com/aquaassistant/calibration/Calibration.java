package com.aquaassistant.calibration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One calibration step of a meter: which point, with which buffer solution, what the meter said and whether it worked. */
@Entity
@Table(name = "calibrations")
public class Calibration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_name")
    private String deviceName;

    private String protocol;

    private String point;

    @Column(name = "buffer_value")
    private Double bufferValue;

    private Double reading;

    private String response;

    private boolean success;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Calibration() {
    }

    public Calibration(Long userId, String deviceName, String protocol, String point, Double bufferValue, Double reading,
                       String response, boolean success) {
        this.userId = userId;
        this.deviceName = deviceName;
        this.protocol = protocol;
        this.point = point;
        this.bufferValue = bufferValue;
        this.reading = reading;
        this.response = response;
        this.success = success;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getDeviceName() { return deviceName; }
    public String getProtocol() { return protocol; }
    public String getPoint() { return point; }
    public Double getBufferValue() { return bufferValue; }
    public Double getReading() { return reading; }
    public String getResponse() { return response; }
    public boolean isSuccess() { return success; }
    public Instant getCreatedAt() { return createdAt; }
}
