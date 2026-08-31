package com.fuoverflow.exam.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * One FE question post. {@code questionText} is null for the common case where the whole question
 * lives in the image; {@code answerOptionIds} is never displayed and exists only so the paper
 * fingerprint stays stable when the delivery shuffles options.
 */
public record IngestQuestion(
        String externalId,
        int displayNo,
        String questionText,
        Integer expectedAnswerCount,
        Integer chapterId,
        BigDecimal mark,
        List<IngestAsset> images,
        List<Long> answerOptionIds
) {
}
