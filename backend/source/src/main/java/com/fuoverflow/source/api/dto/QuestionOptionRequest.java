package com.fuoverflow.source.api.dto;

import jakarta.validation.constraints.Size;

public record QuestionOptionRequest(
        String optionText,
        @Size(max = 500) String optionImageUrl,
        Boolean isCorrect,
        Integer sortOrder
) {
}
