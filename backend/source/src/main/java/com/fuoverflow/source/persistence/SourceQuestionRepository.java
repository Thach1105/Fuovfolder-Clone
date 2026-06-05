package com.fuoverflow.source.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceQuestionRepository extends JpaRepository<SourceQuestionEntity, UUID> {
    List<SourceQuestionEntity> findByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID catalogItemId);

    Optional<SourceQuestionEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<SourceQuestionEntity> findByIdAndCatalogItemIdAndDeletedAtIsNull(UUID id, UUID catalogItemId);

    long countByCatalogItemIdAndDeletedAtIsNull(UUID catalogItemId);
}
