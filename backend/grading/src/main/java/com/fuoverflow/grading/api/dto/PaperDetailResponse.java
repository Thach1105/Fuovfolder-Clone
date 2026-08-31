package com.fuoverflow.grading.api.dto;

import java.util.List;

public record PaperDetailResponse(
        PaperSummaryResponse paper,
        List<Long> unansweredQids,
        List<PaperQuestionResponse> questions
) {
}
