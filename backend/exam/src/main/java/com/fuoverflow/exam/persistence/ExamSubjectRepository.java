package com.fuoverflow.exam.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamSubjectRepository extends JpaRepository<ExamSubjectEntity, UUID> {
    List<ExamSubjectEntity> findByDeletedAtIsNullOrderBySortOrderAscTitleAsc();

    List<ExamSubjectEntity> findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc();

    List<ExamSubjectEntity> findTop6ByCategorySlugAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(
            String categorySlug);

    Optional<ExamSubjectEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ExamSubjectEntity> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    Optional<ExamSubjectEntity> findByCodeIgnoreCaseAndActiveTrueAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(String code, UUID id);
}
