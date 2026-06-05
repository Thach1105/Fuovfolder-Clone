package com.fuoverflow.source.api.dto;

import java.util.List;
import java.util.UUID;

public record PublicQuestionResponse(
        UUID id,
        String questionText,
        String questionImageUrl,
        String explanation,
        boolean multipleCorrect,
        int sortOrder,
        List<PublicQuestionOptionResponse> options
) {
}
