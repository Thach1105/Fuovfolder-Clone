package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminFeQuestionResponse(
        UUID id,
        UUID subjectId,
        String questionText,
        List<String> questionImageUrls,
        String explanation,
        boolean multipleCorrect,
        int sortOrder,
        List<AdminFeOptionResponse> options,
        Instant createdAt,
        Instant updatedAt
) {
    public record AdminFeOptionResponse(
            UUID id,
            String optionText,
            String optionImageUrl,
            boolean isCorrect,
            int sortOrder
    ) {
    }
}
