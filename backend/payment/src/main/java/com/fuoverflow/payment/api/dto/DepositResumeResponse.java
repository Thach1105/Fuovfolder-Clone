package com.fuoverflow.payment.api.dto;

import java.time.Instant;

public record DepositResumeResponse(
        boolean canResume,
        String checkoutUrl,
        Instant expiredAt,
        Long remainingSeconds,
        String reason,
        String message
) {
    public static DepositResumeResponse resumable(String checkoutUrl, Instant expiredAt, long remainingSeconds) {
        return new DepositResumeResponse(true, checkoutUrl, expiredAt, remainingSeconds, null, null);
    }

    public static DepositResumeResponse expired() {
        return new DepositResumeResponse(false, null, null, null, "EXPIRED",
                "Link thanh toán đã hết hạn. Vui lòng tạo đơn mới.");
    }

    public static DepositResumeResponse alreadyPaid() {
        return new DepositResumeResponse(false, null, null, null, "ALREADY_PAID",
                "Đơn hàng này đã được thanh toán.");
    }

    public static DepositResumeResponse notResumable(String reason, String message) {
        return new DepositResumeResponse(false, null, null, null, reason, message);
    }
}
