package com.fuoverflow.award.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PointsLedgerRepository extends JpaRepository<PointsLedgerEntity, UUID> {
    Page<PointsLedgerEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("select coalesce(sum(p.delta), 0) from PointsLedgerEntity p where p.userId = :userId")
    long sumDeltaByUserId(@Param("userId") UUID userId);
}
