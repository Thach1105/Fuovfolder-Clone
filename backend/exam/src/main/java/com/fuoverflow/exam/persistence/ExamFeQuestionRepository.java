package com.fuoverflow.exam.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamFeQuestionRepository extends JpaRepository<ExamFeQuestionEntity, UUID> {
    List<ExamFeQuestionEntity> findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID subjectId);

    Optional<ExamFeQuestionEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ExamFeQuestionEntity> findByIdAndSubjectIdAndDeletedAtIsNull(UUID id, UUID subjectId);

    long countBySubjectIdAndDeletedAtIsNull(UUID subjectId);

    @Query(value = "SELECT q.subject_id FROM exam_fe_questions q WHERE q.deleted_at IS NULL "
            + "AND CAST(q.question_image_urls AS text) LIKE '%' || :key || '%' LIMIT 1",
            nativeQuery = true)
    Optional<UUID> findSubjectIdByQuestionImageUrlsContaining(@Param("key") String key);

    @Modifying
    @Query(value = "UPDATE exam_fe_questions SET view_count = view_count + 1 WHERE id = :id AND deleted_at IS NULL",
           nativeQuery = true)
    void incrementViewCount(@Param("id") UUID id);
}
