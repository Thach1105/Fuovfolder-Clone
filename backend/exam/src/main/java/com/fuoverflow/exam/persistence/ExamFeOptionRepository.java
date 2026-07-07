package com.fuoverflow.exam.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamFeOptionRepository extends JpaRepository<ExamFeOptionEntity, UUID> {
    List<ExamFeOptionEntity> findByQuestionIdOrderBySortOrderAsc(UUID questionId);

    void deleteByQuestionId(UUID questionId);

    @Query(value = """
            SELECT q.subject_id FROM exam_fe_questions q
            JOIN exam_fe_options o ON o.question_id = q.id
            WHERE o.option_image_url = :key AND q.deleted_at IS NULL
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findSubjectIdByOptionImageUrl(@Param("key") String key);
}
