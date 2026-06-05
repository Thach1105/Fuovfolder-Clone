package com.fuoverflow.source.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceQuestionOptionRepository extends JpaRepository<SourceQuestionOptionEntity, UUID> {
    List<SourceQuestionOptionEntity> findByQuestionIdOrderBySortOrderAsc(UUID questionId);

    void deleteByQuestionId(UUID questionId);
}
