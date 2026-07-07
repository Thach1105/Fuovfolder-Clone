package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminCommentResponse(
        UUID id,
        String subjectType,
        UUID subjectId,
        UUID examSubjectId,
        String examSubjectCode,
        UUID authorUserId,
        String authorUsername,
        String authorDisplayName,
        UUID parentCommentId,
        String bodyHtml,
        Instant createdAt,
        Instant updatedAt
) {
}
