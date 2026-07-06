package com.fuoverflow.auth.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, UUID> {

    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

    @Query("""
            SELECT t FROM EmailVerificationTokenEntity t
            WHERE t.userId = :userId
              AND t.verificationCodeHash = :codeHash
              AND t.consumedAt IS NULL
              AND t.expiresAt > :now
            ORDER BY t.createdAt DESC
            LIMIT 1
            """)
    Optional<EmailVerificationTokenEntity> findActiveByUserIdAndCodeHash(UUID userId, String codeHash, Instant now);

    @Query("""
            SELECT t FROM EmailVerificationTokenEntity t
            WHERE t.userId = :userId
              AND t.consumedAt IS NULL
              AND t.expiresAt > :now
            ORDER BY t.createdAt DESC
            LIMIT 1
            """)
    Optional<EmailVerificationTokenEntity> findLatestActiveByUserId(UUID userId, Instant now);

    @Modifying
    @Query("""
            update EmailVerificationTokenEntity t
            set t.consumedAt = :now
            where t.userId = :userId and t.consumedAt is null
            """)
    int consumeActiveByUserId(UUID userId, Instant now);
}
