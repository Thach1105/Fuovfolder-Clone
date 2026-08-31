package com.fuoverflow.exam.api.dto;

import java.util.List;
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
        int fePaperCount,
        int pePaperCount,
        int fePreviewImageCount,
        boolean hasActiveMembership,
        List<PublicPaperSummaryResponse> papers,
        List<PublicSubjectCardResponse> related
) {
}
