package com.fuoverflow.exam.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateFeQuestionRequest(
        String questionText,
        List<String> questionImageUrls,
        String explanation,
        Integer sortOrder,
        @NotEmpty @Valid List<FeOptionRequest> options
) {
}
