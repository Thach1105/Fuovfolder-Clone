package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminSubjectResponse(
        UUID id,
        String code,
        String title,
        String description,
        String coverImageUrl,
        String cardColor,
        String categorySlug,
        int fePreviewImageCount,
        long viewCount,
        boolean active,
        int sortOrder,
        int feQuestionCount,
        int pePaperCount,
        int fePaperCount,
        int pePaperCountAllStatuses,
        LatestPaperSummary latestPaper,
        Instant createdAt,
        Instant updatedAt
) {
    /** The most recently created paper for this subject, in any status — a signal for admins that something needs review. */
    public record LatestPaperSummary(String examCode, String paperType, String status, String term, Instant createdAt) {
    }
}
