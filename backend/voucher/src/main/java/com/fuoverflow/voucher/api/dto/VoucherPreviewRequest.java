package com.fuoverflow.voucher.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record VoucherPreviewRequest(
        @NotBlank String code,
        @NotBlank String transactionType,
        @Min(1) int originalPoints
) {
}
