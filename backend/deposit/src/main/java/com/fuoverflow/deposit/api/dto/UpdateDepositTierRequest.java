package com.fuoverflow.deposit.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateDepositTierRequest(
        @Size(max = 120) String label,
        @Min(1_000) @Max(100_000_000) Integer amountVnd,
        @Min(0) Integer points,
        @Min(0) @Max(100) Integer bonusPercent,
        @Min(0) Integer sortOrder,
        Boolean active
) {
}
