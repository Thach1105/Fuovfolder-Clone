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

    @Test
    void oauthUser_shouldCreateWithEmailVerifiedTrue() {
        // Given
        UUID id = UUID.randomUUID();
        String email = "oauth@gmail.com";
        String normalizedEmail = "oauth@gmail.com";
        String username = "oauthuser";
        String usernameNormalized = "oauthuser";
        String passwordHash = "";
        String displayName = "OAuth User";
        String campus = "HCM";
        boolean emailVerified = true;
        UserStatus status = UserStatus.ACTIVE;
        Instant now = Instant.now();

        // When
        UserEntity user = UserEntity.oauthUser(
            id, email, normalizedEmail, username, usernameNormalized,
            passwordHash, displayName, campus, emailVerified, status, now
        );

        // Then
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getRolesJson()).isEqualTo("[\"USER\"]");
        assertThat(user.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void oauthUser_shouldCreateWithEmailVerifiedFalse() {
        // Given
        UUID id = UUID.randomUUID();
        String email = "unverified.oauth@example.com";
        String normalizedEmail = "unverified.oauth@example.com";
        String username = "unverifiedoauth";
        String usernameNormalized = "unverifiedoauth";
        String passwordHash = "";
        String displayName = "Unverified OAuth";
        String campus = null;
        boolean emailVerified = false;
        UserStatus status = UserStatus.ACTIVE;
        Instant now = Instant.now();

        // When
        UserEntity user = UserEntity.oauthUser(
            id, email, normalizedEmail, username, usernameNormalized,
            passwordHash, displayName, campus, emailVerified, status, now
        );

        // Then
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void oauthUser_shouldRespectProvidedStatus() {
        // Given
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        UserStatus customStatus = UserStatus.ACTIVE;

        // When
        UserEntity user = UserEntity.oauthUser(
            id, "test@example.com", "test@example.com", "user", "user",
            "", "User", "HCM", true, customStatus, now
        );

        // Then
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void oauthUser_shouldHaveUserRoleByDefault() {
        // Given
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        // When
        UserEntity user = UserEntity.oauthUser(
            id, "oauth@example.com", "oauth@example.com", "oauthuser", "oauthuser",
            "", "OAuth User", null, true, UserStatus.ACTIVE, now
        );

        // Then
        assertThat(user.getRolesJson()).isEqualTo("[\"USER\"]");
    }
}
