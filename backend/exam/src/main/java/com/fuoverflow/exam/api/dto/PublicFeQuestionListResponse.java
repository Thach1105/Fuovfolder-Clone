package com.fuoverflow.exam.api.dto;

import java.util.List;

/**
 * FE question list. When {@code locked} is true the caller is not a member and
 * {@code questions} contains only the preview slice; {@code totalCount} reflects the
 * full bank size so the UI can show "x/y (mua membership để xem hết)".
 */
public record PublicFeQuestionListResponse(
        boolean locked,
        int totalCount,
        int previewImageCount,
        List<PublicFeQuestionResponse> questions
) {
}
