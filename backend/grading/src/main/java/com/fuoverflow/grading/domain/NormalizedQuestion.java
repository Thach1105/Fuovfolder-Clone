package com.fuoverflow.grading.domain;

import java.math.BigDecimal;
import java.util.List;

public record NormalizedQuestion(
        long qid,
        PaperSection section,
        Integer qType,
        int displayNo,
        BigDecimal mark,
        Integer chapterId,
        String questionText,
        String imageBase64,
        AnswerMode answerMode,
        Integer expectedAnswerCount,
        List<NormalizedOption> options
) {
}
