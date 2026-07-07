package com.fuoverflow.exam.api.dto;

import java.util.List;
import java.util.UUID;

public record PublicFeQuestionResponse(
        UUID id,
        String questionText,
        List<String> questionImageUrls,
        String explanation,
        boolean multipleCorrect,
        int sortOrder,
        boolean preview,
        List<PublicFeOptionResponse> options
) {
    public record PublicFeOptionResponse(
            UUID id,
            String optionText,
            String optionImageUrl,
            boolean isCorrect
    ) {
    }
}
