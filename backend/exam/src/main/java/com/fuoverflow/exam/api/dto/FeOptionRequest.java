package com.fuoverflow.exam.api.dto;

import jakarta.validation.constraints.Size;

public record FeOptionRequest(
        String optionText,
        @Size(max = 500) String optionImageUrl,
        Boolean isCorrect,
        Integer sortOrder
) {
}
