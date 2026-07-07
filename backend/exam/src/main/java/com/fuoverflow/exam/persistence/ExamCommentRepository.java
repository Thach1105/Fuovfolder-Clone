package com.fuoverflow.exam.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamCommentRepository extends JpaRepository<ExamCommentEntity, UUID> {
    List<ExamCommentEntity> findBySubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            String subjectType, UUID subjectId);

    Optional<ExamCommentEntity> findByIdAndDeletedAtIsNull(UUID id);

    long countBySubjectTypeAndSubjectIdAndDeletedAtIsNull(String subjectType, UUID subjectId);

    // Moderation: newest-first, optionally filtered by subject type or exam subject.
    Page<ExamCommentEntity> findByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    Page<ExamCommentEntity> findBySubjectTypeAndDeletedAtIsNullOrderByCreatedAtDesc(
            String subjectType, Pageable pageable);

    Page<ExamCommentEntity> findByExamSubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID examSubjectId, Pageable pageable);

    Page<ExamCommentEntity> findBySubjectTypeAndExamSubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            String subjectType, UUID examSubjectId, Pageable pageable);
}
