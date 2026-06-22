package com.fuoverflow.user.application;

import com.fuoverflow.user.domain.UserStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterUserCommandTest {

    @Test
    void backwardCompatibleConstructor_shouldDefaultEmailVerifiedFalseAndStatusNull() {
        // Given
        String email = "user@example.com";
        String username = "testuser";
        String passwordHash = "hashed_password";
        String displayName = "Test User";
        String campus = "HCM";

        // When
        RegisterUserCommand command = new RegisterUserCommand(
            email, username, passwordHash, displayName, campus
        );

        // Then
        assertThat(command.email()).isEqualTo(email);
        assertThat(command.username()).isEqualTo(username);
        assertThat(command.passwordHash()).isEqualTo(passwordHash);
        assertThat(command.displayName()).isEqualTo(displayName);
        assertThat(command.campus()).isEqualTo(campus);
        assertThat(command.emailVerified()).isFalse();
        assertThat(command.status()).isNull();
    }

    @Test
    void oauthConstructor_shouldUseProvidedEmailVerifiedAndStatus() {
        // Given
        String email = "oauth.user@gmail.com";
        String username = "oauthuser";
        String passwordHash = ""; // OAuth users may have empty password hash
        String displayName = "OAuth User";
        String campus = null;
        boolean emailVerified = true;
        UserStatus status = UserStatus.ACTIVE;

        // When
        RegisterUserCommand command = new RegisterUserCommand(
            email, username, passwordHash, displayName, campus, emailVerified, status
        );

        // Then
        assertThat(command.email()).isEqualTo(email);
        assertThat(command.username()).isEqualTo(username);
        assertThat(command.passwordHash()).isEqualTo(passwordHash);
        assertThat(command.displayName()).isEqualTo(displayName);
        assertThat(command.campus()).isNull();
        assertThat(command.emailVerified()).isTrue();
        assertThat(command.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void oauthConstructor_shouldAllowEmailVerifiedFalse() {
        // Given
        String email = "unverified@example.com";
        String username = "unverifieduser";
        String passwordHash = "";
        String displayName = "Unverified User";
        String campus = "HN";
        boolean emailVerified = false;
        UserStatus status = UserStatus.ACTIVE;

        // When
        RegisterUserCommand command = new RegisterUserCommand(
            email, username, passwordHash, displayName, campus, emailVerified, status
        );

        // Then
        assertThat(command.emailVerified()).isFalse();
        assertThat(command.status()).isEqualTo(UserStatus.ACTIVE);
    }
}
