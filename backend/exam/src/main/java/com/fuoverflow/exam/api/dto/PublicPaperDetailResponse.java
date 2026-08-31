package com.fuoverflow.exam.api.dto;

import java.util.List;
import java.util.UUID;

/** Full content of one published paper. Member-gated, so image URLs are signed. */
public record PublicPaperDetailResponse(
        UUID id,
        UUID subjectId,
        String subjectCode,
        String type,
        String term,
        String retakeLabel,
        String title,
        String description,
        List<String> imageUrls,
        List<PublicPeItemResponse.PublicPeResourceResponse> resources
) {
}
