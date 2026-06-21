package com.fuoverflow.payment.api.dto;

import java.math.BigDecimal;

public record PaymentStatusResponse(
        String orderCode,
        String status,
        long amountCents,
        String currency,
        long pointsEarned,
        String provider,
        String providerOrderId
) {}
