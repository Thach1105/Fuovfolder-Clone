package com.fuoverflow.voucher.api.dto;

import java.time.Instant;
import java.util.UUID;

public record VoucherResponse(
        UUID id,
        String code,
        String description,
        String discountType,
        int discountValue,
        Integer maxDiscountPoints,
        int minOrderPoints,
        int maxUsage,
        int usedCount,
        int maxUsagePerUser,
        String applicableTypes,
        String requiredMembershipSlugs,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
