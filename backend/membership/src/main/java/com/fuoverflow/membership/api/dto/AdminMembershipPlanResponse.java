package com.fuoverflow.membership.api.dto;

import java.util.UUID;

public record AdminMembershipPlanResponse(
        UUID id,
        String slug,
        String name,
        String description,
        int pricePoints,
        String currency,
        String billingInterval,
        String status,
        String roleSlug,
        int durationDays
) {
}
