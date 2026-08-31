package com.fuoverflow.exam.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * A validated exam paper ready to be persisted. FE papers carry {@code questions} and no
 * images/resources; PE papers carry {@code images} and/or {@code resources} and no questions.
 */
public record IngestPaper(
        String examCode,
        ExamPaperType paperType,
        String subjectCode,
        String term,
        String retakeLabel,
        String title,
        String description,
        Integer durationMinutes,
        BigDecimal totalMark,
        Integer declaredQuestionCount,
        String sourceSystem,
        String externalPaperId,
        List<IngestQuestion> questions,
        List<IngestAsset> images,
        List<IngestResource> resources
) {
}
