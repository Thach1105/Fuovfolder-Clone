package com.fuoverflow.membership.api.dto;

import java.time.Instant;
import java.util.UUID;

public record MembershipStatusResponse(
        UUID membershipId,
        String planSlug,
        String planName,
        String roleSlug,
        Instant expiresAt,
        boolean active
) {
}
