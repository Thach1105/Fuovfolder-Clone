package com.fuoverflow.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverrideEntity, UUID> {
    @Query("""
            SELECT o FROM UserPermissionOverrideEntity o
            WHERE o.userId = :userId
              AND o.revokedAt IS NULL
              AND o.startsAt <= :now
              AND (o.endsAt IS NULL OR o.endsAt > :now)
            """)
    List<UserPermissionOverrideEntity> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
