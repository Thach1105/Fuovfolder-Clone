package com.fuoverflow.exam.api.dto;

import java.util.UUID;

public record PublicSubjectCardResponse(
        UUID id,
        String code,
        String title,
        String categorySlug,
        String cardColor,
        String coverImageUrl,
        long viewCount,
        int feQuestionCount,
        int pePaperCount
) {
}
