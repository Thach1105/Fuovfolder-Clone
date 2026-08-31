package com.fuoverflow.grading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradingPaperQuestionRepository extends JpaRepository<GradingPaperQuestionEntity, UUID> {
    List<GradingPaperQuestionEntity> findByPaperIdOrderByDisplayNoAsc(UUID paperId);

    Optional<GradingPaperQuestionEntity> findByPaperIdAndQid(UUID paperId, long qid);
}
