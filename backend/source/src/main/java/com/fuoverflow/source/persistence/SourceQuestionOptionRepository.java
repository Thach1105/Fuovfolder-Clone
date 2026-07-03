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

    @Query("""
            SELECT q.catalogItemId FROM SourceQuestionEntity q
            JOIN SourceQuestionOptionEntity o ON o.questionId = q.id
            WHERE o.optionImageUrl = :key AND q.deletedAt IS NULL
            """)
    Optional<UUID> findCatalogItemIdByOptionImageUrl(@Param("key") String key);
}
