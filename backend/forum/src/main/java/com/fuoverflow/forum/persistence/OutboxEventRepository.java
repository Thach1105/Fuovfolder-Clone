package com.fuoverflow.forum.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
            String status, Instant availableAt, Pageable pageable);
}
