package com.fuoverflow.forum.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PollOptionRepository extends JpaRepository<PollOptionEntity, UUID> {
    List<PollOptionEntity> findByThreadIdOrderBySortOrderAsc(UUID threadId);
}
