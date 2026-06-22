package com.fuoverflow.user.api.dto;

import com.fuoverflow.user.domain.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthUserView(
        UUID id,
        String email,
        String username,
        String passwordHash,
        String displayName,
        UserStatus status,
        List<String> roles,
        long permVersion,
        List<String> permissions,
        boolean superAdmin,
        boolean emailVerified,
        Instant emailVerifiedAt,
        Instant passwordChangedAt,
        Instant deletedAt
) {
}
