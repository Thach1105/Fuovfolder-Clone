package com.fuoverflow.grading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GradingPaperAnswerRepository extends JpaRepository<GradingPaperAnswerEntity, UUID> {
    List<GradingPaperAnswerEntity> findByPaperIdOrderByQidAscOptionIndexAsc(UUID paperId);

    List<GradingPaperAnswerEntity> findByPaperIdAndQid(UUID paperId, long qid);
}
