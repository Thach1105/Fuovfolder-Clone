package com.fuoverflow.grading.api.dto;

import java.util.List;

public record PreviewQuestion(
        long qid,
        int displayNo,
        String section,
        String answerMode,
        Integer expectedAnswerCount,
        String questionText,
        String imageBase64,
        List<PreviewOption> options
) {
}
