package com.fuoverflow.source.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        UUID catalogItemId,
        String code,
        String title,
        String status,
        int unitPricePoints,
        int accessDays,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        Instant createdAt
) {
}
