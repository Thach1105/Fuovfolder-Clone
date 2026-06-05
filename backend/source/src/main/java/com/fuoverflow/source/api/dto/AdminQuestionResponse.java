package com.fuoverflow.source.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminQuestionResponse(
        UUID id,
        UUID catalogItemId,
        String questionText,
        String questionImageUrl,
        String explanation,
        boolean multipleCorrect,
        int sortOrder,
        List<AdminQuestionOptionResponse> options,
        Instant createdAt,
        Instant updatedAt
) {
}
