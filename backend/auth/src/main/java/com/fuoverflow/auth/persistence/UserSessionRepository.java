package com.fuoverflow.auth.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;import java.util.Optional;import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity,UUID>{
 Optional<UserSessionEntity> findByRefreshTokenHash(String refreshTokenHash);
 Optional<UserSessionEntity> findByIdAndRevokedAtIsNull(UUID id);
 @Modifying @Query("update UserSessionEntity s set s.revokedAt=:now, s.revokedReason=:reason where s.userId=:userId and s.revokedAt is null")
 int revokeAllByUserId(UUID userId,String reason,Instant now);
 @Modifying @Query("update UserSessionEntity s set s.revokedAt=:now, s.revokedReason=:reason where s.refreshTokenFamilyId=:familyId and s.revokedAt is null")
 int revokeFamily(UUID familyId,String reason,Instant now);
 @Modifying @Query("update UserSessionEntity s set s.revokedAt = :now, s.revokedReason = :reason where s.userId = :userId and s.id != :excludeSessionId and s.revokedAt is null")
 int revokeAllExcept(UUID userId, UUID excludeSessionId, String reason, Instant now);
}
