package com.fuoverflow.auth.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "verification_code_hash")
    private String verificationCodeHash;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static EmailVerificationTokenEntity create(UUID id, UUID userId, String tokenHash,
                                                      String codeHash, Instant expiresAt,
                                                      Instant now, int maxAttempts) {
        var e = new EmailVerificationTokenEntity();
        e.id = id;
        e.userId = userId;
        e.tokenHash = tokenHash;
        e.verificationCodeHash = codeHash;
        e.expiresAt = expiresAt;
        e.createdAt = now;
        e.attemptCount = 0;
        e.maxAttempts = maxAttempts;
        return e;
    }

    public UUID getUserId() { return userId; }

    public String getTokenHash() { return tokenHash; }

    public String getVerificationCodeHash() { return verificationCodeHash; }

    public Instant getExpiresAt() { return expiresAt; }

    public Instant getConsumedAt() { return consumedAt; }

    public int getAttemptCount() { return attemptCount; }

    public int getMaxAttempts() { return maxAttempts; }

    public boolean activeAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public boolean isLockedOut() {
        return attemptCount >= maxAttempts;
    }

    public void incrementAttempts() {
        attemptCount++;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}
