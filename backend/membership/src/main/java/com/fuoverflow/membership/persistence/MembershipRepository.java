package com.fuoverflow.membership.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {
    @Query("""
            SELECT m FROM MembershipEntity m
            WHERE m.userId = :userId AND m.status = 'active'
              AND m.currentPeriodEnd > :now
            ORDER BY m.currentPeriodEnd DESC
            """)
    List<MembershipEntity> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    Optional<MembershipEntity> findTopByUserIdAndStatusOrderByCurrentPeriodEndDesc(UUID userId, String status);
}
