package com.fuoverflow.forum.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ForumSyncRunRepository extends JpaRepository<ForumSyncRunEntity, UUID> {
    Page<ForumSyncRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
