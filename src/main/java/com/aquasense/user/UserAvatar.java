package com.aquasense.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Profile photo of a user: a square JPEG of 256 px, re-encoded by the server (without metadata). */
@Entity
@Table(name = "user_avatars")
public class UserAvatar {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private byte[] image;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAvatar() {
    }

    public UserAvatar(Long userId, byte[] image, Instant updatedAt) {
        this.userId = userId;
        this.image = image;
        this.updatedAt = updatedAt;
    }

    public void replace(byte[] image, Instant updatedAt) {
        this.image = image;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() { return userId; }
    public byte[] getImage() { return image; }
    public Instant getUpdatedAt() { return updatedAt; }
}
