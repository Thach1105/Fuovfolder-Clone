package com.fuoverflow.moderation.api.dto;

import java.time.Instant;
import java.util.UUID;

public record FlagResponse(
        UUID id,
        UUID reporterUserId,
        String targetType,
        UUID targetId,
        String reason,
        String note,
        String status,
        Instant createdAt
) {
}
