package com.fuoverflow.exam.api.dto.webhook;

import java.math.BigDecimal;
import java.util.List;

public record QuestionPayload(
        String externalId,
        Integer displayNo,
        String questionText,
        Integer expectedAnswerCount,
        Integer chapterId,
        BigDecimal mark,
        List<AssetPayload> images,
        List<Long> answerOptionIds
) {
}
