package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.PasswordResetTokenEntity;
import com.fuoverflow.auth.persistence.PasswordResetTokenRepository;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.TooManyRequestsException;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.common.support.ResendRateLimiter;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserPasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class SetPasswordService {
    private static final String PURPOSE = "SET_PASSWORD";

    private final UserLookupService users;
    private final UserPasswordService userPasswords;
    private final PasswordResetTokenRepository tokens;
    private final UserSessionRepository sessions;
    private final TokenGenerator generator;
    private final TokenHashing hashing;
    private final PasswordService passwords;
    private final SetPasswordEmailSender emailSender;
    private final AuthProperties properties;
    private final ResendRateLimiter rateLimiter;

    public SetPasswordService(UserLookupService users,
                               UserPasswordService userPasswords,
                               PasswordResetTokenRepository tokens,
                               UserSessionRepository sessions,
                               TokenGenerator generator,
                               TokenHashing hashing,
                               PasswordService passwords,
                               SetPasswordEmailSender emailSender,
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
    public void requestSetPassword(UUID userId) {
        var user = users.findAuthUserById(userId)
                .orElseThrow(() -> new BadRequestException("USER_NOT_FOUND", "User not found"));
        if (user.passwordHash() != null) {
            throw new BadRequestException("PASSWORD_ALREADY_SET",
                    "Tài khoản này đã có mật khẩu. Hãy sử dụng tính năng đổi mật khẩu.");
        }
        try {
            rateLimiter.checkAndRecord("set_password", userId);
        } catch (TooManyRequestsException e) {
            throw e;
        }
        Instant now = Instant.now();
        tokens.consumeActiveByUserIdAndPurpose(userId, PURPOSE, now);
        String token = generator.opaqueToken();
        tokens.save(PasswordResetTokenEntity.createSetPassword(
                UUID.randomUUID(), userId, hashing.hash(token),
                now.plus(tokenTtl()), now));
        emailSender.send(user.email(), user.displayName(), token);
    }

    @Transactional
    public void confirmSetPassword(String token, String newPassword) {
        Instant now = Instant.now();
        PasswordResetTokenEntity entity = tokens.findByTokenHashAndPurpose(hashing.hash(token), PURPOSE)
                .orElseThrow(() -> new UnauthorizedException("TOKEN_INVALID", "Token không hợp lệ"));
        if (!entity.activeAt(now)) {
            throw new UnauthorizedException("TOKEN_EXPIRED", "Token đã hết hạn hoặc đã được sử dụng");
        }
        var user = users.findAuthUserById(entity.getUserId())
                .orElseThrow(() -> new UnauthorizedException("TOKEN_INVALID", "Token không hợp lệ"));
        if (user.passwordHash() != null) {
            throw new BadRequestException("PASSWORD_ALREADY_SET",
                    "Tài khoản này đã có mật khẩu.");
        }
        passwords.validatePolicy(newPassword);
        userPasswords.updatePassword(user.id(), passwords.encode(newPassword), now);
        entity.consume(now);
        tokens.consumeActiveByUserIdAndPurpose(user.id(), PURPOSE, now);
        sessions.revokeAllByUserId(user.id(), "SET_PASSWORD", now);
    }

    private Duration tokenTtl() {
        AuthProperties.PasswordReset config = properties.passwordReset();
        if (config == null || config.tokenTtl() == null) {
            return Duration.ofHours(1);
        }
        return config.tokenTtl();
    }
}
