package com.aquasense.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Record of one import of official data. */
@Entity
@Table(name = "reference_imports")
public class ReferenceImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private int stations;
    private int readings;
    private boolean success;
    private String message;

    protected ReferenceImport() {
    }

    public ReferenceImport(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void finish(int stations, int readings, boolean success, String message) {
        this.finishedAt = Instant.now();
        this.stations = stations;
        this.readings = readings;
        this.success = success;
        this.message = message == null ? null : message.length() > 300 ? message.substring(0, 300) : message;
    }

    public Long getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getStations() { return stations; }
    public int getReadings() { return readings; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
