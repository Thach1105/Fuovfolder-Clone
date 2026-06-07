package com.fuoverflow.post.api.dto;

import com.fuoverflow.material.api.dto.AttachmentResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostResponse(
        UUID id,
        UUID threadId,
        UUID authorUserId,
        UUID parentPostId,
        String authorHandle,
        String authorAvatarUrl,
        String bodyMd,
        String bodyHtml,
        String status,
        int editVersion,
        int reactionCount,
        String sourceUrl,
        List<AttachmentResponse> attachments,
        Instant createdAt,
        Instant lastEditedAt
) {
}
