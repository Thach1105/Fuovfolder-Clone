package com.fuoverflow.auth.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    Optional<PasswordResetTokenEntity> findByTokenHashAndPurpose(String tokenHash, String purpose);

    @Modifying
    @Query("""
            update PasswordResetTokenEntity t
            set t.consumedAt = :now
            where t.userId = :userId and t.consumedAt is null
            """)
    int consumeActiveByUserId(UUID userId, Instant now);

    @Modifying
    @Query("""
            update PasswordResetTokenEntity t
            set t.consumedAt = :now
            where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null
            """)
    int consumeActiveByUserIdAndPurpose(UUID userId, String purpose, Instant now);
}
