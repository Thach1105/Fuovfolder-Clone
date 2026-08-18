package com.fuoverflow.grading.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record PaperQuestionResponse(
        long qid,
        String section,
        Integer qType,
        int displayNo,
        BigDecimal mark,
        String questionText,
        boolean hasImage,
        String answerMode,
        Integer expectedAnswerCount,
        String answerSource,
        boolean answered,
        List<PaperOptionResponse> options
) {
}
