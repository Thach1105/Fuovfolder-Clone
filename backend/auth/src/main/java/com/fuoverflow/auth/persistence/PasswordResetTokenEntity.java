package com.fuoverflow.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenEntity {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "purpose", nullable = false)
    private String purpose = "RESET_PASSWORD";

    public static PasswordResetTokenEntity create(UUID id, UUID userId, String hash, Instant expiresAt, Instant now) {
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.id = id;
        entity.userId = userId;
        entity.tokenHash = hash;
        entity.expiresAt = expiresAt;
        entity.createdAt = now;
        return entity;
    }

    public static PasswordResetTokenEntity createSetPassword(UUID id, UUID userId, String hash, Instant expiresAt, Instant now) {
        PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
        entity.id = id;
        entity.userId = userId;
        entity.tokenHash = hash;
        entity.expiresAt = expiresAt;
        entity.createdAt = now;
        entity.purpose = "SET_PASSWORD";
        return entity;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }

    public boolean activeAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public String getPurpose() { return purpose; }
}
