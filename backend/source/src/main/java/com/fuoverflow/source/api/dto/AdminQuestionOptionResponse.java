package com.fuoverflow.source.api.dto;

import java.util.UUID;

public record AdminQuestionOptionResponse(
        UUID id,
        String optionText,
        String optionImageUrl,
        boolean isCorrect,
        int sortOrder
) {
}
