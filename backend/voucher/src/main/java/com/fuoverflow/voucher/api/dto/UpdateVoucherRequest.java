package com.fuoverflow.voucher.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateVoucherRequest(
        @NotBlank @Size(max = 50) String code,
        String description,
        @NotBlank String discountType,
        @Min(1) int discountValue,
        Integer maxDiscountPoints,
        @Min(0) int minOrderPoints,
        @Min(1) int maxUsage,
        @Min(1) int maxUsagePerUser,
        @NotBlank String applicableTypes,
        String requiredMembershipSlugs,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
}
