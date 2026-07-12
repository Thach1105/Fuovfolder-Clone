package com.fuoverflow.exam.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamCommentLikeRepository extends JpaRepository<ExamCommentLikeEntity, UUID> {
    Optional<ExamCommentLikeEntity> findByCommentIdAndUserId(UUID commentId, UUID userId);

    boolean existsByCommentIdAndUserId(UUID commentId, UUID userId);

    List<ExamCommentLikeEntity> findByCommentIdInAndUserId(List<UUID> commentIds, UUID userId);
}
