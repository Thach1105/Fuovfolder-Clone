package com.fuoverflow.exam.api.dto;

import java.util.UUID;

public record PublicSubjectDetailResponse(
        UUID id,
        String code,
        String title,
        String description,
        String categorySlug,
        String cardColor,
        String coverImageUrl,
        long viewCount,
        int feQuestionCount,
        int pePaperCount,
        int fePreviewImageCount,
        boolean hasActiveMembership
) {
}
