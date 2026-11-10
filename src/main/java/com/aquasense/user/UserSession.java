package com.aquasense.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One use of the application: it starts when the user logs in (or registers) and ends when they log out, when the
 * token expires or when the account's sessions are closed for security. The actions done meanwhile point to it.
 */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    /** Why a session ended. */
    public enum EndReason {
        LOGOUT("Cerró sesión"),
        CLOSED_FROM_OTHER("Cerrada desde otra sesión"),
        PASSWORD_CHANGED("Cerrada al cambiar la contraseña"),
        ROLE_CHANGED("Cerrada al cambiar el rol"),
        ACCOUNT_DISABLED("Cerrada al desactivar la cuenta");

        private final String label;

        EndReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason")
    private EndReason endReason;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    private String client;

    protected UserSession() {
    }

    public UserSession(Long userId, Instant startedAt, Instant expiresAt, boolean rememberMe, String client) {
        this.userId = userId;
        this.startedAt = startedAt;
        this.lastActivityAt = startedAt;
        this.expiresAt = expiresAt;
        this.rememberMe = rememberMe;
        this.client = client;
    }

    /** Open: not ended and not expired. */
    public boolean isOpen(Instant now) {
        return endedAt == null && now.isBefore(expiresAt);
    }

    public void touch(Instant now) {
        if (now.isAfter(lastActivityAt)) {
            lastActivityAt = now;
        }
    }

    public void end(EndReason reason, Instant now) {
        if (endedAt == null) {
            endedAt = now;
            endReason = reason;
            touch(now);
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getEndedAt() { return endedAt; }
    public EndReason getEndReason() { return endReason; }
    public boolean isRememberMe() { return rememberMe; }
    public String getClient() { return client; }
}
