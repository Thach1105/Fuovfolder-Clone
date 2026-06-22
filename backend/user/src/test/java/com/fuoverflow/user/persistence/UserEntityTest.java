package com.fuoverflow.user.persistence;

import com.fuoverflow.user.domain.UserRole;
import com.fuoverflow.user.domain.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityTest {

    @Test
    void pendingUser_shouldHaveEmailVerifiedFalse() {
        // Given
        UUID id = UUID.randomUUID();
        String email = "test@example.com";
        String normalizedEmail = "test@example.com";
        String username = "testuser";
        String usernameNormalized = "testuser";
        String passwordHash = "hashed_password";
        String displayName = "Test User";
        String campus = "HCM";
        Instant now = Instant.now();

        // When
        UserEntity user = UserEntity.pending(
            id, email, normalizedEmail, username, usernameNormalized,
            passwordHash, displayName, campus, now
        );

        // Then
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    }

    @Test
    void seededAdministrator_shouldHaveEmailVerifiedTrue() {
        // Given
        UUID id = UUID.randomUUID();
        String email = "admin@fuoverflow.com";
        String normalizedEmail = "admin@fuoverflow.com";
        String username = "admin";
        String usernameNormalized = "admin";
        String passwordHash = "hashed_password";
        String displayName = "Administrator";
        Instant now = Instant.now();

        // When
        UserEntity admin = UserEntity.seededAdministrator(
            id, email, normalizedEmail, username, usernameNormalized,
            passwordHash, displayName, now
        );

        // Then
        assertThat(admin.isEmailVerified()).isTrue();
        assertThat(admin.getEmailVerifiedAt()).isEqualTo(now);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void markEmailVerified_shouldSetEmailVerifiedTrue() {
        // Given
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        UserEntity user = UserEntity.pending(
            id, "test@example.com", "test@example.com", "testuser", "testuser",
            "hash", "Test User", "HCM", now
        );
        assertThat(user.isEmailVerified()).isFalse();

        // When
        Instant verifiedAt = now.plusSeconds(3600);
        user.markEmailVerified(verifiedAt);

        // Then
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
