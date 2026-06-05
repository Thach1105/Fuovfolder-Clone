package com.fuoverflow.source.api.dto;

import java.util.UUID;

public record PublicQuestionOptionResponse(
        UUID id,
        String optionText,
        String optionImageUrl,
        boolean isCorrect
) {
}
