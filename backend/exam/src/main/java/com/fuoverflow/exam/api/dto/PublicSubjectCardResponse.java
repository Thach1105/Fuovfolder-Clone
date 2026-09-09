package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PublicSubjectCardResponse(
        UUID id,
        String code,
        String title,
        String categorySlug,
        String cardColor,
        String coverImageUrl,
        long viewCount,
        int fePaperCount,
        int pePaperCount,
        Integer curriculumTerm,
        LatestPaperSummary latestPaper
) {
    public record LatestPaperSummary(String examCode, String paperType, Instant createdAt) {
    }
}
