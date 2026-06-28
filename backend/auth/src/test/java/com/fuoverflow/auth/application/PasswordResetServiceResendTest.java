package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.PasswordResetTokenRepository;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.common.exception.TooManyRequestsException;
import com.fuoverflow.common.support.ResendRateLimiter;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserPasswordService;
import com.fuoverflow.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PasswordResetServiceResendTest {

    private UserLookupService users;
    private PasswordResetTokenRepository tokens;
    private ResendRateLimiter rateLimiter;
    private PasswordResetEmailSender emailSender;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        users = mock(UserLookupService.class);
        tokens = mock(PasswordResetTokenRepository.class);
        rateLimiter = mock(ResendRateLimiter.class);
        emailSender = mock(PasswordResetEmailSender.class);
        service = new PasswordResetService(
                users,
                mock(UserPasswordService.class),
                tokens,
                mock(UserSessionRepository.class),
                mock(TokenGenerator.class),
                mock(TokenHashing.class),
                mock(PasswordService.class),
                emailSender,
                null,
                rateLimiter);
    }

    @Test
    void requestResetShouldApplyRateLimit() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = activeUser(userId, "user@example.com");
        when(users.findAuthUserByIdentifier("user@example.com")).thenReturn(Optional.of(user));

        service.requestReset("user@example.com");

        verify(rateLimiter).checkAndRecord("password_reset", userId);
    }

    @Test
    void requestResetShouldNotCallRateLimiterForUnknownEmail() {
        when(users.findAuthUserByIdentifier("unknown@example.com")).thenReturn(Optional.empty());

        service.requestReset("unknown@example.com");

        verifyNoInteractions(rateLimiter);
    }

    @Test
    void requestResetShouldPropagateRateLimitException() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = activeUser(userId, "user@example.com");
        when(users.findAuthUserByIdentifier("user@example.com")).thenReturn(Optional.of(user));
        doThrow(new TooManyRequestsException("RESEND_TOO_SOON", "Vui lòng chờ 60 giây."))
                .when(rateLimiter).checkAndRecord("password_reset", userId);

        assertThatThrownBy(() -> service.requestReset("user@example.com"))
                .isInstanceOf(TooManyRequestsException.class);

        verifyNoInteractions(emailSender);
    }

    private AuthUserView activeUser(UUID id, String email) {
        return new AuthUserView(id, email, "user", null, "User", UserStatus.ACTIVE,
                List.of(), 0, List.of(), false, true, Instant.now(), null, null);
    }
}
