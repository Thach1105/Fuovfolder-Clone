package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminWebhookEventResponse(
        UUID id,
        String clientId,
        String eventId,
        String status,
        int attemptCount,
        UUID paperId,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant processedAt
) {
}
