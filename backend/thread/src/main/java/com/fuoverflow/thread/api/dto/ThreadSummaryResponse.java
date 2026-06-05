package com.fuoverflow.thread.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ThreadSummaryResponse(
        UUID id,
        UUID forumId,
        UUID categoryId,
        String title,
        String slug,
        String status,
        String threadType,
        String authorHandle,
        String sourceUrl,
        String campus,
        String semester,
        String materialType,
        String tags,
        int replyCount,
        long viewCount,
        Instant lastPostAt,
        Instant createdAt
) {
}
