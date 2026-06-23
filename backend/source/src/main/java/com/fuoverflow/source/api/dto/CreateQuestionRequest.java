package com.fuoverflow.source.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateQuestionRequest(
        String questionText,
        @Size(max = 500) String questionImageUrl,
        List<String> questionImageUrls,
        String explanation,
        Integer sortOrder,
        @NotEmpty @Valid List<QuestionOptionRequest> options
) {
}
