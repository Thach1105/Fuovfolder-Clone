package com.fuoverflow.auth.application;

import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.ApiException;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserPasswordService;
import com.fuoverflow.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceTest {

    @Mock UserLookupService users;
    @Mock UserPasswordService userPasswords;
    @Mock UserSessionRepository sessions;
    @Mock PasswordService passwords;

    @InjectMocks ChangePasswordService service;

    private UUID userId;
    private UUID sessionId;
    private AuthUserView userWithPassword;
    private AuthUserView userWithoutPassword;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        userWithPassword = new AuthUserView(userId, "test@example.com", "user", "hashed_pass",
                "Test", UserStatus.ACTIVE, List.of("USER"), 1L, List.of(), false, true, now, now, null);
        userWithoutPassword = new AuthUserView(userId, "oauth@example.com", "oauthuser", null,
                "OAuth", UserStatus.ACTIVE, List.of("USER"), 1L, List.of(), false, true, now, null, null);
    }

    @Test
    void changePassword_success() {
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(userWithPassword));
        when(passwords.matches("oldPass", "hashed_pass")).thenReturn(true);
        when(passwords.encode("newPass123")).thenReturn("new_hashed");

        service.changePassword(userId, sessionId, "oldPass", "newPass123");

        verify(userPasswords).updatePassword(eq(userId), eq("new_hashed"), any(Instant.class));
        verify(sessions).revokeAllExcept(eq(userId), eq(sessionId), eq("CHANGE_PASSWORD"), any(Instant.class));
    }

    @Test
    void changePassword_throwsWhenNoPassword() {
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(userWithoutPassword));

        assertThatThrownBy(() -> service.changePassword(userId, sessionId, "any", "newPass123"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("NO_PASSWORD_TO_CHANGE"));
    }

    @Test
    void changePassword_throwsWhenWrongCurrentPassword() {
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(userWithPassword));
        when(passwords.matches("wrongPass", "hashed_pass")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(userId, sessionId, "wrongPass", "newPass123"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("WRONG_PASSWORD"));
    }
}
