package com.fuoverflow.exam.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamPaperRepository extends JpaRepository<ExamPaperEntity, UUID> {

    Optional<ExamPaperEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ExamPaperEntity> findByFingerprintAndDeletedAtIsNull(String fingerprint);

    Optional<ExamPaperEntity> findByExamCodeIgnoreCaseAndDeletedAtIsNull(String examCode);

    List<ExamPaperEntity> findBySubjectIdAndStatusAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(
            UUID subjectId, String status);

    List<ExamPaperEntity> findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(UUID subjectId);

    List<ExamPaperEntity> findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(String status);

    List<ExamPaperEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();

    long countBySubjectIdAndPaperTypeAndStatusAndDeletedAtIsNull(
            UUID subjectId, String paperType, String status);

    long countBySubjectIdAndPaperTypeAndDeletedAtIsNull(UUID subjectId, String paperType);

    Optional<ExamPaperEntity> findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID subjectId);

    Optional<ExamPaperEntity> findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID subjectId, String status);
}
