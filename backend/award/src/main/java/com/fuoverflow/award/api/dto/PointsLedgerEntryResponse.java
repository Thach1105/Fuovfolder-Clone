package com.fuoverflow.award.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PointsLedgerEntryResponse(
        UUID id,
        int delta,
        String reason,
        String sourceType,
        UUID sourceId,
        Instant createdAt
) {
}
