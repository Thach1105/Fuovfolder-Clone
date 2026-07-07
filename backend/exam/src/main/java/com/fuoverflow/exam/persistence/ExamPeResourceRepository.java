package com.fuoverflow.exam.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamPeResourceRepository extends JpaRepository<ExamPeResourceEntity, UUID> {
    List<ExamPeResourceEntity> findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID peItemId);

    Optional<ExamPeResourceEntity> findByIdAndDeletedAtIsNull(UUID id);
}
