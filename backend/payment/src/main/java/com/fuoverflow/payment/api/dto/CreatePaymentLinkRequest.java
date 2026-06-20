package com.fuoverflow.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePaymentLinkRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be >= 0.01")
        BigDecimal amount,

        @NotNull(message = "returnUrl is required")
        String returnUrl,

        @NotNull(message = "cancelUrl is required")
        String cancelUrl,

        String description
) {
}
