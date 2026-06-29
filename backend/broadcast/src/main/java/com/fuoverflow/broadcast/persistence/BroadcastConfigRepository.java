package com.fuoverflow.broadcast.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BroadcastConfigRepository extends JpaRepository<BroadcastConfigEntity, UUID> {
    Optional<BroadcastConfigEntity> findByEventType(String eventType);
}
