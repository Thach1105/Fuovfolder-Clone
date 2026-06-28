package com.fuoverflow.auth.application;

import com.fuoverflow.auth.persistence.EmailVerificationTokenEntity;
import com.fuoverflow.auth.persistence.EmailVerificationTokenRepository;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.common.exception.TooManyRequestsException;
import com.fuoverflow.common.support.ResendRateLimiter;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserEmailVerificationService;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmailVerificationServiceResendTest {

    private EmailVerificationTokenRepository repository;
    private TokenGenerator generator;
    private TokenHashing hashing;
    private UserEmailVerificationService users;
    private UserLookupService userLookup;
    private VerificationEmailSender emailSender;
    private ResendRateLimiter rateLimiter;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmailVerificationTokenRepository.class);
        generator = mock(TokenGenerator.class);
        hashing = mock(TokenHashing.class);
        users = mock(UserEmailVerificationService.class);
        userLookup = mock(UserLookupService.class);
        emailSender = mock(VerificationEmailSender.class);
        rateLimiter = mock(ResendRateLimiter.class);
        service = new EmailVerificationService(repository, generator, hashing, users, userLookup, emailSender, rateLimiter);
    }

    @Test
    void resendShouldDoNothingForUnknownEmail() {
        when(userLookup.findAuthUserByIdentifier("unknown@example.com")).thenReturn(Optional.empty());

        service.resend("unknown@example.com");

        verifyNoInteractions(rateLimiter, repository, emailSender);
    }

    @Test
    void resendShouldDoNothingIfAlreadyVerified() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = verifiedUser(userId);
        when(userLookup.findAuthUserByIdentifier("user@example.com")).thenReturn(Optional.of(user));

        service.resend("user@example.com");

        verifyNoInteractions(rateLimiter, repository, emailSender);
    }

    @Test
    void resendShouldInvalidateOldTokenAndSendNew() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = unverifiedUser(userId);
        when(userLookup.findAuthUserByIdentifier("user@example.com")).thenReturn(Optional.of(user));
        when(generator.opaqueToken()).thenReturn("new-token");
        when(hashing.hash("new-token")).thenReturn("new-token-hash");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.resend("user@example.com");

        verify(rateLimiter).checkAndRecord("email_verify", userId);
        verify(repository).consumeActiveByUserId(eq(userId), any(Instant.class));
        verify(repository).save(any(EmailVerificationTokenEntity.class));
        verify(emailSender).send("user@example.com", "Test User", "new-token");
    }

    @Test
    void resendShouldPropagateRateLimitException() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = unverifiedUser(userId);
        when(userLookup.findAuthUserByIdentifier("user@example.com")).thenReturn(Optional.of(user));
        doThrow(new TooManyRequestsException("RESEND_TOO_SOON", "Vui lòng chờ 60 giây."))
                .when(rateLimiter).checkAndRecord("email_verify", userId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.resend("user@example.com"))
                .isInstanceOf(TooManyRequestsException.class);

        verifyNoInteractions(repository, emailSender);
    }

    private AuthUserView verifiedUser(UUID id) {
        return new AuthUserView(id, "user@example.com", "testuser", null,
                "Test User", UserStatus.ACTIVE, List.of(), 0, List.of(), false, true, Instant.now(), null, null);
    }

    private AuthUserView unverifiedUser(UUID id) {
        return new AuthUserView(id, "user@example.com", "testuser", null,
                "Test User", UserStatus.PENDING_EMAIL_VERIFICATION, List.of(), 0, List.of(), false, false, null, null, null);
    }
}
