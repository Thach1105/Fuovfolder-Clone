package com.fuoverflow.auth.application;

import com.fuoverflow.auth.api.dto.ForgotPasswordResponse;
import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.PasswordResetTokenEntity;
import com.fuoverflow.auth.persistence.PasswordResetTokenRepository;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.common.support.ResendRateLimiter;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserPasswordService;
import com.fuoverflow.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class PasswordResetService {
    private static final String GENERIC_MESSAGE =
            "If an account exists for this email, a password reset link has been sent.";

    private final UserLookupService users;
    private final UserPasswordService userPasswords;
    private final PasswordResetTokenRepository tokens;
    private final UserSessionRepository sessions;
    private final TokenGenerator generator;
    private final TokenHashing hashing;
    private final PasswordService passwords;
    private final PasswordResetEmailSender emailSender;
    private final AuthProperties properties;
    private final ResendRateLimiter rateLimiter;

    public PasswordResetService(
            UserLookupService users,
            UserPasswordService userPasswords,
            PasswordResetTokenRepository tokens,
            UserSessionRepository sessions,
            TokenGenerator generator,
            TokenHashing hashing,
            PasswordService passwords,
            PasswordResetEmailSender emailSender,
            AuthProperties properties,
            ResendRateLimiter rateLimiter) {
        this.users = users;
        this.userPasswords = userPasswords;
        this.tokens = tokens;
        this.sessions = sessions;
        this.generator = generator;
        this.hashing = hashing;
        this.passwords = passwords;
        this.emailSender = emailSender;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public ForgotPasswordResponse requestReset(String email) {
        users.findAuthUserByIdentifier(email)
                .filter(this::canResetPassword)
                .ifPresent(user -> {
                    rateLimiter.checkAndRecord("password_reset", user.id());
                    Instant now = Instant.now();
                    tokens.consumeActiveByUserId(user.id(), now);
                    String token = generator.opaqueToken();
                    tokens.save(PasswordResetTokenEntity.create(
                            UUID.randomUUID(),
                            user.id(),
                            hashing.hash(token),
                            now.plus(tokenTtl()),
                            now));
                    emailSender.send(user.email(), user.displayName(), token);
                });
        return new ForgotPasswordResponse(GENERIC_MESSAGE);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = Instant.now();
        PasswordResetTokenEntity entity = tokens.findByTokenHash(hashing.hash(token))
                .orElseThrow(() -> new UnauthorizedException("TOKEN_INVALID", "Reset token is invalid"));
        if (!entity.activeAt(now)) {
            throw new UnauthorizedException("TOKEN_EXPIRED", "Reset token is expired");
        }
        AuthUserView user = users.findAuthUserById(entity.getUserId())
                .orElseThrow(() -> new UnauthorizedException("TOKEN_INVALID", "Reset token is invalid"));
        if (!canResetPassword(user)) {
            throw new UnauthorizedException("TOKEN_INVALID", "Reset token is invalid");
        }
        String passwordHash = passwords.encode(newPassword);
        userPasswords.updatePassword(user.id(), passwordHash, now);
        entity.consume(now);
        tokens.consumeActiveByUserId(user.id(), now);
        sessions.revokeAllByUserId(user.id(), "PASSWORD_RESET", now);
    }

    private boolean canResetPassword(AuthUserView user) {
        return user.deletedAt() == null
                && (user.status() == UserStatus.ACTIVE || user.status() == UserStatus.PENDING_EMAIL_VERIFICATION);
    }

    private Duration tokenTtl() {
        AuthProperties.PasswordReset config = properties != null ? properties.passwordReset() : null;
        if (config == null || config.tokenTtl() == null) {
            return Duration.ofHours(1);
        }
        return config.tokenTtl();
    }
}
