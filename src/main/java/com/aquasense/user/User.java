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

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private Plan plan;

    @Column(name = "premium_until")
    private Instant premiumUntil;

    @Column(name = "premium_reminder_for")
    private Instant premiumReminderFor;

    @Column(name = "token_version")
    private int tokenVersion;

    @Column(name = "created_at")
    private Instant createdAt;

    private boolean enabled = true;

    @Column(name = "disabled_reason")
    private String disabledReason;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    /** Demonstration account of the login screen (see {@code DemoAccounts}). */
    private boolean demo;

    protected User() {
    }

    public User(String email, String fullName, String passwordHash, Role role) {
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.plan = Plan.FREE;
        this.createdAt = Instant.now();
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        revokeTokens();
    }

    /** Activates the administrator role and closes the open sessions so the new role takes effect. */
    public void promoteToAdmin() {
        this.role = Role.ADMIN;
        revokeTokens();
    }

    /** {@code at}: time of the new photo, or {@code null} when it is removed. */
    public void avatarChanged(Instant at) {
        this.avatarUpdatedAt = at;
    }

    public Instant getAvatarUpdatedAt() {
        return avatarUpdatedAt;
    }

    public boolean isDemo() {
        return demo;
    }

    public void markDemo() {
        this.demo = true;
    }

    public void revokeTokens() {
        this.tokenVersion++;
    }

    /** Manual plan change (administration): Premium without expiry, or back to Free. */
    public void changePlan(Plan newPlan) {
        changePlan(newPlan, null);
    }

    /**
     * Manual plan change (administration). Premium with an expiry date ({@code until}) or without one ({@code null});
     * Free clears any expiry.
     */
    public void changePlan(Plan newPlan, Instant until) {
        this.plan = newPlan;
        this.premiumUntil = newPlan == Plan.PREMIUM ? until : null;
        this.premiumReminderFor = null;
    }

    /** Removes the administrator role and closes their sessions so the change takes effect right away. */
    public void demoteToUser() {
        this.role = Role.USER;
        revokeTokens();
    }

    /** Disables the account: it cannot log in and its open sessions stop being valid. */
    public void disable(String reason, Instant now) {
        this.enabled = false;
        this.disabledReason = reason;
        this.disabledAt = now;
        revokeTokens();
    }

    public void enable() {
        this.enabled = true;
        this.disabledReason = null;
        this.disabledAt = null;
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
    }

    /** Premium assigned by an administrator, without an expiry date. */
    public boolean isManualPremium() {
        return plan == Plan.PREMIUM && premiumUntil == null;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Plan getPlan() { return plan; }
    public Instant getPremiumUntil() { return premiumUntil; }
    public Instant getPremiumReminderFor() { return premiumReminderFor; }

    /**
     * Extends Premium for a paid period. If Premium was already active with a valid expiry, it adds from that date;
     * if it was assigned without expiry by an administrator, it stays that way.
     */
    public Instant extendPremium(java.time.Duration period, Instant now) {
        if (plan == Plan.PREMIUM && premiumUntil == null) {
            return null;
        }
        Instant base = plan == Plan.PREMIUM && premiumUntil != null && premiumUntil.isAfter(now) ? premiumUntil : now;
        this.plan = Plan.PREMIUM;
        this.premiumUntil = base.plus(period);
        return premiumUntil;
    }

    /** Goes back to Free if the paid period ended. */
    public boolean expirePremiumIfDue(Instant now) {
        if (plan == Plan.PREMIUM && premiumUntil != null && !premiumUntil.isAfter(now)) {
            this.plan = Plan.FREE;
            this.premiumUntil = null;
            this.premiumReminderFor = null;
            return true;
        }
        return false;
    }

    public void markPremiumReminderSent() {
        this.premiumReminderFor = premiumUntil;
    }
    public int getTokenVersion() { return tokenVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isEnabled() { return enabled; }
    public String getDisabledReason() { return disabledReason; }
    public Instant getDisabledAt() { return disabledAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
