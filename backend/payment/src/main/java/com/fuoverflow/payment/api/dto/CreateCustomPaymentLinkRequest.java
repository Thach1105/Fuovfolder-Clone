package com.fuoverflow.payment.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

public record CreateCustomPaymentLinkRequest(
        @NotNull(message = "Số tiền nạp không được để trống")
        @Min(value = 10_000, message = "Số tiền nạp tối thiểu là 10.000đ")
        @Max(value = 499_999_999, message = "Số tiền nạp tối đa là 499.999.999đ")
        Integer amountVnd,
        @NotBlank @URL String returnUrl,
        @NotBlank @URL String cancelUrl
) {
}
