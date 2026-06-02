package com.fuoverflow.user.application;

public record RegisterUserCommand(
        String email,
        String username,
        String passwordHash,
        String displayName,
        String campus
) {
}
