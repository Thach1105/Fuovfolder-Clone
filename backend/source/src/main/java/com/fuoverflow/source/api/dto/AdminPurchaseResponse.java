package com.fuoverflow.source.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminPurchaseResponse(
        UUID id,
        UUID userId,
        String username,
        String displayName,
        UUID catalogItemId,
        String code,
        String title,
        String status,
        int unitPricePoints,
        int accessDays,
        Instant startsAt,
        Instant endsAt,
        boolean refunded,
        UUID paymentLedgerId,
        UUID refundLedgerId,
        String refundReason,
        Instant createdAt
) {
}
