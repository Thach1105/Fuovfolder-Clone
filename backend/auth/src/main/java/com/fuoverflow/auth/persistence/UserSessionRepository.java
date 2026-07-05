package com.fuoverflow.auth.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;import java.util.List;import java.util.Optional;import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity,UUID>{
 Optional<UserSessionEntity> findByRefreshTokenHash(String refreshTokenHash);
 Optional<UserSessionEntity> findByIdAndRevokedAtIsNull(UUID id);
 @Query("select s.refreshTokenFamilyId from UserSessionEntity s where s.userId=:userId and s.revokedAt is null and s.refreshExpiresAt>:now group by s.refreshTokenFamilyId order by min(s.issuedAt) asc")
 List<UUID> findActiveFamilyIdsOrderedByAge(UUID userId, Instant now);
 @Modifying @Query("update UserSessionEntity s set s.revokedAt=:now, s.revokedReason=:reason where s.userId=:userId and s.revokedAt is null")
 int revokeAllByUserId(@Param("userId") UUID userId, @Param("reason") String reason, @Param("now") Instant now);
 @Modifying @Query("update UserSessionEntity s set s.revokedAt=:now, s.revokedReason=:reason where s.refreshTokenFamilyId=:familyId and s.revokedAt is null")
 int revokeFamily(@Param("familyId") UUID familyId, @Param("reason") String reason, @Param("now") Instant now);
 @Modifying @Query("update UserSessionEntity s set s.revokedAt = :now, s.revokedReason = :reason where s.userId = :userId and s.id != :excludeSessionId and s.revokedAt is null")
 int revokeAllExcept(@Param("userId") UUID userId, @Param("excludeSessionId") UUID excludeSessionId, @Param("reason") String reason, @Param("now") Instant now);
 @Query("""
     SELECT s FROM UserSessionEntity s
     WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.refreshExpiresAt > :now
     AND s.replacedBySessionId IS NULL
     ORDER BY s.lastUsedAt DESC NULLS LAST, s.issuedAt DESC
     """)
 List<UserSessionEntity> findActiveSessionsForUser(@Param("userId") UUID userId, @Param("now") Instant now);
 @Modifying @Query("UPDATE UserSessionEntity s SET s.revokedAt = :now, s.revokedReason = :reason WHERE s.userId = :userId AND s.refreshTokenFamilyId != :excludeFamilyId AND s.revokedAt IS NULL")
 int revokeAllExceptFamily(@Param("userId") UUID userId, @Param("excludeFamilyId") UUID excludeFamilyId, @Param("reason") String reason, @Param("now") Instant now);
}
