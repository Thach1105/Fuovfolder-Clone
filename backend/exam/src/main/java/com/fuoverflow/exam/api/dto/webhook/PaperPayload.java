package com.fuoverflow.exam.api.dto.webhook;

import java.math.BigDecimal;
import java.util.List;

/**
 * One paper. {@code questions} belongs to FE deliveries; {@code images} and {@code resources}
 * belong to PE deliveries. Sending the wrong set for the declared type is rejected rather than
 * silently ignored, so a mis-shaped delivery never lands as a half-empty paper.
 */
public record PaperPayload(
        String examCode,
        String paperType,
        String subjectCode,
        String term,
        String retakeLabel,
        String title,
        String description,
        Integer durationMinutes,
        BigDecimal totalMark,
        Integer declaredQuestionCount,
        PaperSourcePayload source,
        List<QuestionPayload> questions,
        List<AssetPayload> images,
        List<ResourcePayload> resources
) {
}
