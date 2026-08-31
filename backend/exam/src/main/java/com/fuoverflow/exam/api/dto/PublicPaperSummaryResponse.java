package com.fuoverflow.exam.api.dto;

import java.util.UUID;

/** One published paper as it appears in a subject's paper list. */
public record PublicPaperSummaryResponse(
        UUID id,
        String type,
        String term,
        String retakeLabel,
        String title,
        int imageCount,
        int resourceCount
) {
}
