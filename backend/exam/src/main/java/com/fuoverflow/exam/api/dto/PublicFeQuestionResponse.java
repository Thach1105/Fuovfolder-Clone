package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicFeQuestionResponse(
        UUID id,
        String questionText,
        int totalImageCount,
        List<PublicImageItem> images,
        int sortOrder,
        int commentCount,
        Instant createdAt
) {
    public record PublicImageItem(
            int index,
            String url,
            String type
    ) {
    }
}
