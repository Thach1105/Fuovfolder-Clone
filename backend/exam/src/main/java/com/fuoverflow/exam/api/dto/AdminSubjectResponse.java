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
        int fePreviewCount,
        long viewCount,
        boolean active,
        int sortOrder,
        int feQuestionCount,
        int pePaperCount,
        Instant createdAt,
        Instant updatedAt
) {
}
