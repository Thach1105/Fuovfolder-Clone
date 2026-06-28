package com.fuoverflow.payment.api.dto;

import java.time.Instant;

public record DepositHistoryItemResponse(
        String orderId,
        String orderCode,
        long amount,
        String currency,
        String status,
        Integer pointsAwarded,
        String tierLabel,
        boolean canResume,
        Instant expiredAt,
        Instant createdAt,
        Instant paidAt
) {}
