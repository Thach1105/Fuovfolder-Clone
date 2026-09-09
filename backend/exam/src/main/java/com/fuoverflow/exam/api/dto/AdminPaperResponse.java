package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminPaperResponse(
        UUID id,
        UUID subjectId,
        String paperType,
        String examCode,
        String term,
        String retakeLabel,
        String title,
        String status,
        String ingestSource,
        int questionCount,
        int resourceCount,
        Instant publishedAt,
        Instant createdAt,
        String campus
) {
}
