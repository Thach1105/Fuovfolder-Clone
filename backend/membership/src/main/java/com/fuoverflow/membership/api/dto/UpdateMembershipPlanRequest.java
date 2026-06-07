package com.fuoverflow.membership.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateMembershipPlanRequest(
        @NotBlank String name,
        String description,
        @Min(1) int pricePoints,
        String billingInterval,
        @NotBlank String roleSlug,
        @Min(1) int durationDays,
        String status,
        String imageUrl
) {
}
