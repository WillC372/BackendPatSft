package com.aquaassistant.reminder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/** "Mide hoy", "limpia el filtro"...: repeats every N days and notifies on the phone and/or by email when due. */
@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id")
    private Long placeId;

    @Enumerated(EnumType.STRING)
    private ReminderKind kind;

    private String title;

    @Column(name = "every_days")
    private int everyDays;

    @Column(name = "next_due")
    private Instant nextDue;

    @Column(name = "by_email")
    private boolean byEmail;

    @Column(name = "by_push")
    private boolean byPush;

    private boolean active;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Reminder() {
    }

    public Reminder(Long userId, Long placeId, ReminderKind kind, String title, int everyDays, Instant nextDue,
                    boolean byEmail, boolean byPush) {
        this.userId = userId;
        this.placeId = placeId;
        this.createdAt = Instant.now();
        this.active = true;
        update(placeId, kind, title, everyDays, nextDue, byEmail, byPush, true);
    }

    public void update(Long placeId, ReminderKind kind, String title, int everyDays, Instant nextDue, boolean byEmail,
                       boolean byPush, boolean active) {
        this.placeId = placeId;
        this.kind = kind;
        this.title = title;
        this.everyDays = everyDays;
        if (nextDue != null && !nextDue.equals(this.nextDue)) {
            this.nextDue = nextDue;
            this.lastNotifiedAt = null;
        }
        this.byEmail = byEmail;
        this.byPush = byPush;
        this.active = active;
    }

    /** Done: the next one is counted from now. */
    public void done(Instant now) {
        this.nextDue = now.plus(Duration.ofDays(everyDays));
        this.lastNotifiedAt = null;
    }

    public boolean isDue(Instant now) {
        return active && !nextDue.isAfter(now);
    }

    /** Due and not yet notified for this due date. */
    public boolean needsNotice(Instant now) {
        return isDue(now) && (lastNotifiedAt == null || lastNotifiedAt.isBefore(nextDue));
    }

    public void notified(Instant now) {
        this.lastNotifiedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getPlaceId() { return placeId; }
    public ReminderKind getKind() { return kind; }
    public String getTitle() { return title; }
    public int getEveryDays() { return everyDays; }
    public Instant getNextDue() { return nextDue; }
    public boolean isByEmail() { return byEmail; }
    public boolean isByPush() { return byPush; }
    public boolean isActive() { return active; }
    public Instant getLastNotifiedAt() { return lastNotifiedAt; }
}
