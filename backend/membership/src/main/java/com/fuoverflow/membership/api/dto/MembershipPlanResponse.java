package com.fuoverflow.membership.api.dto;

import java.time.Instant;
import java.util.UUID;

public record MembershipPlanResponse(
        UUID id,
        String slug,
        String name,
        String description,
        int pricePoints,
        String currency,
        String billingInterval,
        String roleSlug,
        int durationDays,
        String imageUrl
) {
}
