package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ExamCommentResponse(
        UUID id,
        String subjectType,
        UUID subjectId,
        UUID authorUserId,
        String authorUsername,
        String authorDisplayName,
        String authorAvatarUrl,
        UUID parentCommentId,
        String bodyHtml,
        int likeCount,
        boolean likedByMe,
        boolean editable,
        Instant createdAt,
        Instant updatedAt
) {
}
