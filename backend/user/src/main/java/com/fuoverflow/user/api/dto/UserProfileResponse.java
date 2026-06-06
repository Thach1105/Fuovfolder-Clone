package com.fuoverflow.user.api.dto;

import com.fuoverflow.user.domain.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        String firstName,
        String lastName,
        String avatarUrl,
        UserStatus status,
        List<String> roles,
        long permVersion,
        List<String> permissions,
        boolean superAdmin,
        boolean emailVerified,
        Instant createdAt
) {
}
