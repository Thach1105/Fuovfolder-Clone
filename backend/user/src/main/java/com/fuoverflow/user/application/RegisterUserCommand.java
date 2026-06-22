package com.fuoverflow.user.application;

import com.fuoverflow.user.domain.UserStatus;

public record RegisterUserCommand(
        String email,
        String username,
        String passwordHash,
        String displayName,
        String campus,
        boolean emailVerified,
        UserStatus status
) {
    // Backward-compatible constructor for password registration
    public RegisterUserCommand(String email, String username, String passwordHash, String displayName, String campus) {
        this(email, username, passwordHash, displayName, campus, false, null);
    }
}
