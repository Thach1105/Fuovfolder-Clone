package com.fuoverflow.post.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PostResponse(
        UUID id,
        UUID threadId,
        UUID authorUserId,
        UUID parentPostId,
        String authorHandle,
        String bodyMd,
        String bodyHtml,
        String status,
        int editVersion,
        int reactionCount,
        String sourceUrl,
        Instant createdAt,
        Instant lastEditedAt
) {
}
