package com.fuoverflow.grading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradingPaperRepository extends JpaRepository<GradingPaperEntity, UUID> {
    Optional<GradingPaperEntity> findByFingerprintAndDeletedAtIsNull(String fingerprint);

    Optional<GradingPaperEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<GradingPaperEntity> findByPayloadSha256AndDeletedAtIsNull(String payloadSha256);

    List<GradingPaperEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();

    List<GradingPaperEntity> findBySubjectCodeIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtDesc(String subjectCode);
}
