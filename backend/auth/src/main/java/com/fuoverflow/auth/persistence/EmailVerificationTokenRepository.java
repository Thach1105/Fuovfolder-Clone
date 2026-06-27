package com.fuoverflow.auth.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, UUID> {

    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update EmailVerificationTokenEntity t
            set t.consumedAt = :now
            where t.userId = :userId and t.consumedAt is null
            """)
    int consumeActiveByUserId(UUID userId, Instant now);
}
