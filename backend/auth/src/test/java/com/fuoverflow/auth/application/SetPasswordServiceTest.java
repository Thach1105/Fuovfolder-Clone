package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.PasswordResetTokenEntity;
import com.fuoverflow.auth.persistence.PasswordResetTokenRepository;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.common.exception.ApiException;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.TooManyRequestsException;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.common.support.ResendRateLimiter;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetPasswordServiceTest {

    @Mock UserLookupService users;
    @Mock UserPasswordService userPasswords;
    @Mock PasswordResetTokenRepository tokens;
    @Mock UserSessionRepository sessions;
    @Mock TokenGenerator generator;
    @Mock TokenHashing hashing;
    @Mock PasswordService passwords;
    @Mock SetPasswordEmailSender emailSender;
    @Mock AuthProperties properties;
    @Mock ResendRateLimiter rateLimiter;

    @InjectMocks SetPasswordService service;

    private UUID userId;
    private AuthUserView oauthUser;
    private AuthUserView passwordUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Instant now = Instant.now();
        oauthUser = new AuthUserView(userId, "oauth@example.com", "oauthuser", null,
                "OAuth User", UserStatus.ACTIVE, List.of("USER"), 1L, List.of(), false, true, now, null, null);
        passwordUser = new AuthUserView(userId, "user@example.com", "user", "hashed",
                "User", UserStatus.ACTIVE, List.of("USER"), 1L, List.of(), false, true, now, now, null);
    }

    @Test
    void requestSetPassword_success() {
        AuthProperties.PasswordReset pwReset = mock(AuthProperties.PasswordReset.class);
        when(pwReset.tokenTtl()).thenReturn(Duration.ofHours(1));
        when(properties.passwordReset()).thenReturn(pwReset);

        when(users.findAuthUserById(userId)).thenReturn(Optional.of(oauthUser));
        when(generator.opaqueToken()).thenReturn("rawtoken");
        when(hashing.hash("rawtoken")).thenReturn("hashed_token");
        doNothing().when(rateLimiter).checkAndRecord(anyString(), any(UUID.class));

        service.requestSetPassword(userId);

        verify(tokens).consumeActiveByUserIdAndPurpose(eq(userId), eq("SET_PASSWORD"), any(Instant.class));
        verify(tokens).save(any(PasswordResetTokenEntity.class));
        verify(emailSender).send("oauth@example.com", "OAuth User", "rawtoken");
    }

    @Test
    void requestSetPassword_throwsWhenAlreadyHasPassword() {
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(passwordUser));

        assertThatThrownBy(() -> service.requestSetPassword(userId))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("PASSWORD_ALREADY_SET"));
    }

    @Test
    void requestSetPassword_throwsWhenRateLimited() {
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(oauthUser));
        doThrow(new TooManyRequestsException("RESEND_TOO_SOON", "Vui lòng chờ"))
                .when(rateLimiter).checkAndRecord(anyString(), any(UUID.class));

        assertThatThrownBy(() -> service.requestSetPassword(userId))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void confirmSetPassword_throwsWhenTokenInvalid() {
        when(hashing.hash("bad_token")).thenReturn("bad_hash");
        when(tokens.findByTokenHashAndPurpose("bad_hash", "SET_PASSWORD")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmSetPassword("bad_token", "newPass123"))
                .isInstanceOf(UnauthorizedException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("TOKEN_INVALID"));
    }
}
