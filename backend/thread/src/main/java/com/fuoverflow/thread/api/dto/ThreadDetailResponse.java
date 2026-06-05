package com.fuoverflow.thread.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ThreadDetailResponse(
        UUID id,
        UUID forumId,
        UUID categoryId,
        String title,
        String slug,
        String status,
        String authorHandle,
        String sourceUrl,
        int replyCount,
        long viewCount,
        int reactionCount,
        Instant lastPostAt,
        Instant createdAt,
        Instant updatedAt
) {
}
