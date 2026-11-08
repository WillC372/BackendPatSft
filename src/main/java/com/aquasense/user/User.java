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

    /** Activa el rol de administrador y cierra las sesiones abiertas para que el nuevo rol entre en vigor. */
    public void promoteToAdmin() {
        this.role = Role.ADMIN;
        revokeTokens();
    }

    public void revokeTokens() {
        this.tokenVersion++;
    }

    /** Cambio manual de plan (administración): Premium sin vencimiento, o vuelta a Gratuito. */
    public void changePlan(Plan newPlan) {
        changePlan(newPlan, null);
    }

    /**
     * Cambio manual de plan (administración). Premium con fecha de vencimiento ({@code until}) o sin ella ({@code null});
     * Gratuito borra cualquier vencimiento.
     */
    public void changePlan(Plan newPlan, Instant until) {
        this.plan = newPlan;
        this.premiumUntil = newPlan == Plan.PREMIUM ? until : null;
        this.premiumReminderFor = null;
    }

    /** Quita el rol de administrador y cierra sus sesiones para que el cambio rija de inmediato. */
    public void demoteToUser() {
        this.role = Role.USER;
        revokeTokens();
    }

    /** Desactiva la cuenta: no puede iniciar sesión y sus sesiones abiertas dejan de valer. */
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

    /** Premium asignado por un administrador, sin fecha de vencimiento. */
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
     * Extiende Premium por un periodo pagado. Si ya tenía Premium con vencimiento vigente, se suma desde esa fecha;
     * si lo tenía asignado sin vencimiento por un administrador, se mantiene así.
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

    /** Vuelve a Gratuito si el periodo pagado terminó. */
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
