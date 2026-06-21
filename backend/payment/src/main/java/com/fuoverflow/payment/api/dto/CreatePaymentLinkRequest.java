package com.fuoverflow.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePaymentLinkRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "1000", message = "amount must be >= 1000 VND")
        @Digits(integer = 10, fraction = 0, message = "amount must be a whole-number VND value")
        BigDecimal amount,

        @NotBlank(message = "returnUrl is required")
        String returnUrl,

        @NotBlank(message = "cancelUrl is required")
        String cancelUrl,

        @Size(max = 255, message = "description must be <= 255 characters")
        String description
) {
}
