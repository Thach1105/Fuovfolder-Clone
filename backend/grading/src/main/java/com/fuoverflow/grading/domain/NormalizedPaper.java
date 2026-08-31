package com.fuoverflow.grading.domain;

import java.math.BigDecimal;
import java.util.List;

public record NormalizedPaper(
        String examCode,
        String subjectCode,
        Integer durationMinutes,
        BigDecimal totalMark,
        int declaredQuestionCount,
        List<NormalizedQuestion> questions
) {
}
