package com.fuoverflow.payment.api.dto;

import java.time.Instant;

public record AdminOrderListItemResponse(
        String orderId,
        String orderCode,
        long amount,
        String currency,
        String status,
        Integer pointsAwarded,
        String tierLabel,
        String userId,
        String username,
        String email,
        Instant createdAt,
        Instant paidAt
) {}
