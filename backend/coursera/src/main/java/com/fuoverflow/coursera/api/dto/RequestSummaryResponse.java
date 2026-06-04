package com.fuoverflow.coursera.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequestSummaryResponse(
        UUID id,
        String status,
        int totalPoints,
        String catalogCode,
        String catalogTitle,
        String username,
        Instant createdAt,
        Instant statusChangedAt
) {
}
