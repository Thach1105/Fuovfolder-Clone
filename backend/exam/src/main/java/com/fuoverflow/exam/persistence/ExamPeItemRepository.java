package com.fuoverflow.exam.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamPeItemRepository extends JpaRepository<ExamPeItemEntity, UUID> {
    List<ExamPeItemEntity> findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID subjectId);

    Optional<ExamPeItemEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ExamPeItemEntity> findByIdAndSubjectIdAndDeletedAtIsNull(UUID id, UUID subjectId);

    long countBySubjectIdAndDeletedAtIsNull(UUID subjectId);

    List<ExamPeItemEntity> findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID paperId);

    long countByPaperIdAndDeletedAtIsNull(UUID paperId);

    @Query(value = "SELECT p.subject_id FROM exam_pe_items p WHERE p.deleted_at IS NULL "
            + "AND CAST(p.exam_image_urls AS text) LIKE '%' || :key || '%' LIMIT 1",
            nativeQuery = true)
    Optional<UUID> findSubjectIdByExamImageUrlsContaining(@Param("key") String key);
}
