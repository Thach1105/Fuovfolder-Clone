package com.fuoverflow.grading.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PaperSummaryResponse(
        UUID id,
        String examCode,
        String subjectCode,
        String status,
        int questionCount,
        int answeredCount,
        int unansweredCount,
        Instant createdAt,
        Instant publishedAt
) {
}
