package com.fuoverflow.source.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceQuestionOptionRepository extends JpaRepository<SourceQuestionOptionEntity, UUID> {
    List<SourceQuestionOptionEntity> findByQuestionIdOrderBySortOrderAsc(UUID questionId);

    void deleteByQuestionId(UUID questionId);

    @Query(value = """
            SELECT q.catalog_item_id FROM source_questions q
            JOIN source_question_options o ON o.question_id = q.id
            WHERE o.option_image_url = :key AND q.deleted_at IS NULL
            LIMIT 1
            """, nativeQuery = true)
    Optional<UUID> findCatalogItemIdByOptionImageUrl(@Param("key") String key);
}
