package com.fuoverflow.broadcast.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BroadcastEventRepository extends JpaRepository<BroadcastEventEntity, UUID> {
}
