package com.fuoverflow.exam.api.dto;

import java.util.List;

public record CreateFeQuestionRequest(
        String questionText,
        List<String> questionImageUrls,
        List<String> questionBlurUrls,
        Integer sortOrder
) {
}
