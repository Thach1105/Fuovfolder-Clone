package com.fuoverflow.source.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceQuestionRepository extends JpaRepository<SourceQuestionEntity, UUID> {
    List<SourceQuestionEntity> findByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID catalogItemId);

    Optional<SourceQuestionEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<SourceQuestionEntity> findByIdAndCatalogItemIdAndDeletedAtIsNull(UUID id, UUID catalogItemId);

    long countByCatalogItemIdAndDeletedAtIsNull(UUID catalogItemId);

    @Query("SELECT q.catalogItemId FROM SourceQuestionEntity q WHERE q.questionImageUrl = :key AND q.deletedAt IS NULL")
    Optional<UUID> findCatalogItemIdByQuestionImageUrl(@Param("key") String key);

    @Query(value = "SELECT q.catalog_item_id FROM source_questions q WHERE q.deleted_at IS NULL AND CAST(q.question_image_urls AS text) LIKE '%' || :key || '%'",
            nativeQuery = true)
    Optional<UUID> findCatalogItemIdByQuestionImageUrlsContaining(@Param("key") String key);
}
