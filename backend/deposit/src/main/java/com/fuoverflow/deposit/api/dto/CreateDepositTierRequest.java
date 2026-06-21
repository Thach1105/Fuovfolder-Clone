package com.fuoverflow.deposit.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDepositTierRequest(
        @NotBlank @Size(max = 120) String label,
        @NotNull @Min(1_000) @Max(100_000_000) Integer amountVnd,
        @NotNull @Min(0) Integer points,
        @NotNull @Min(0) @Max(100) Integer bonusPercent,
        @NotNull @Min(0) Integer sortOrder,
        Boolean active
) {
    public boolean isActive() {
        return active == null || active;
    }
}
