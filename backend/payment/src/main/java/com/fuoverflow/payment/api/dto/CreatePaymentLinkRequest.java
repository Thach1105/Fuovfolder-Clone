package com.fuoverflow.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

public record CreatePaymentLinkRequest(
        @NotNull UUID tierId,
        @NotBlank @URL String returnUrl,
        @NotBlank @URL String cancelUrl
) {
}
