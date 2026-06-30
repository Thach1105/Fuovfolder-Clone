package com.fuoverflow.voucher.api.dto;

import java.time.Instant;
import java.util.UUID;

public record VoucherRedemptionResponse(
        UUID id,
        UUID voucherId,
        UUID userId,
        String transactionType,
        UUID transactionId,
        int originalPoints,
        int discountPoints,
        int finalPoints,
        Instant createdAt
) {
}
