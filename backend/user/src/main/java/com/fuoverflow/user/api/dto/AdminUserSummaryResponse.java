package com.fuoverflow.user.api.dto;

import com.fuoverflow.user.domain.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserSummaryResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        UserStatus status,
        List<String> roles,
        boolean emailVerified,
        Instant createdAt,
        Instant lastLoginAt
) {
}
