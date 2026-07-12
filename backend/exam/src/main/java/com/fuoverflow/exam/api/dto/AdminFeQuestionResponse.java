package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminFeQuestionResponse(
        UUID id,
        UUID subjectId,
        String questionText,
        List<String> questionImageUrls,
        List<String> questionBlurUrls,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}
