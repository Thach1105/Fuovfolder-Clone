package com.fuoverflow.payment.api.dto;

import java.time.Instant;
import java.util.List;

public record AdminOrderDetailResponse(
        String orderId,
        String orderCode,
        long amount,
        String currency,
        String status,
        Integer pointsAwarded,
        String tierLabel,
        String checkoutUrl,
        Instant expiredAt,
        Instant createdAt,
        Instant updatedAt,
        UserSummary user,
        List<PaymentRecord> payments
) {
    public record UserSummary(String userId, String username, String email, String displayName) {}
    public record PaymentRecord(String paymentId, String status, int amountCents, String currency, Instant paidAt, Instant createdAt) {}
}
