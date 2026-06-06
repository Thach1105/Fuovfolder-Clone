package com.fuoverflow.moderation.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContentFlagRepository extends JpaRepository<ContentFlagEntity, UUID> {
    Page<ContentFlagEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Optional<ContentFlagEntity> findByReporterUserIdAndTargetTypeAndTargetIdAndStatus(
            UUID reporterUserId, String targetType, UUID targetId, String status);
}
