package com.fuoverflow.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignmentEntity, UUID> {
    @Query("""
            SELECT ra FROM RoleAssignmentEntity ra
            WHERE ra.userId = :userId
              AND ra.revokedAt IS NULL
              AND ra.startsAt <= :now
              AND (ra.endsAt IS NULL OR ra.endsAt > :now)
            """)
    List<RoleAssignmentEntity> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
            SELECT ra FROM RoleAssignmentEntity ra
            WHERE ra.userId = :userId AND ra.roleId = :roleId AND ra.scopeType = 'global'
              AND ra.revokedAt IS NULL
            """)
    List<RoleAssignmentEntity> findActiveGlobalByUserAndRole(@Param("userId") UUID userId,
                                                             @Param("roleId") UUID roleId);
}
