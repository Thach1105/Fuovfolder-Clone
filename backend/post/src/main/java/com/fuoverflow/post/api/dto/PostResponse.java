package com.fuoverflow.post.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PostResponse(
        UUID id,
        UUID threadId,
        String authorHandle,
        String bodyHtml,
        String status,
        int editVersion,
        int reactionCount,
        String sourceUrl,
        Instant createdAt
) {
}
