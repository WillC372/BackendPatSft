package com.aquasense.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Purpose purpose = Purpose.PASSWORD_RESET;

    /** Para qué sirve el enlace enviado por correo. */
    public enum Purpose { PASSWORD_RESET, ADMIN_VERIFY }

    protected PasswordResetToken() {
    }

    public PasswordResetToken(Long userId, String tokenHash, Instant expiresAt) {
        this(userId, tokenHash, expiresAt, Purpose.PASSWORD_RESET);
    }

    public PasswordResetToken(Long userId, String tokenHash, Instant expiresAt, Purpose purpose) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.purpose = purpose;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public Long getUserId() { return userId; }
    public Purpose getPurpose() { return purpose; }
}
