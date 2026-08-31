package com.fuoverflow.grading.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Everything the admin UI needs to review a converted paper before anything is stored.
 *
 * @param collision {@code NONE}, {@code EXISTING_DRAFT} or {@code EXISTING_PUBLISHED} so the UI can
 *                  say up front whether saving creates a paper or merges into an existing draft
 */
public record PaperPreviewResponse(
        String examCode,
        String subjectCode,
        Integer durationMinutes,
        BigDecimal totalMark,
        int questionCount,
        String fingerprint,
        String collision,
        UUID collisionPaperId,
        int existingAnsweredCount,
        List<PreviewQuestion> questions,
        List<String> warnings
) {
}
