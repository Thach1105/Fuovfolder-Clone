package com.fuoverflow.auth.application;

import com.fuoverflow.auth.persistence.PasswordResetTokenEntity;
import com.fuoverflow.auth.persistence.PasswordResetTokenRepository;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserPasswordService;
import com.fuoverflow.user.validation.EmailNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class PasswordResetService {
    private final PasswordResetTokenRepository repository;
    private final TokenGenerator generator;
    private final TokenHashing hashing;
    private final UserLookupService userLookupService;
    private final UserPasswordService userPasswordService;
    private final UserSessionRepository sessionRepository;
    private final PasswordService passwordService;
    private final PasswordResetEmailSender emailSender;
    private final EmailNormalizer emailNormalizer;

    public PasswordResetService(PasswordResetTokenRepository repository,
                               TokenGenerator generator,
                               TokenHashing hashing,
                               UserLookupService userLookupService,
                               UserPasswordService userPasswordService,
                               UserSessionRepository sessionRepository,
                               PasswordService passwordService,
                               PasswordResetEmailSender emailSender,
                               EmailNormalizer emailNormalizer) {
        this.repository = repository;
        this.generator = generator;
        this.hashing = hashing;
        this.userLookupService = userLookupService;
        this.userPasswordService = userPasswordService;
        this.sessionRepository = sessionRepository;
        this.passwordService = passwordService;
        this.emailSender = emailSender;
        this.emailNormalizer = emailNormalizer;
    }

    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = emailNormalizer.normalize(email);
        var userOpt = userLookupService.findAuthUserByIdentifier(normalizedEmail);

        // Always return success to avoid leaking user existence
        if (userOpt.isEmpty()) {
            return;
        }

        AuthUserView user = userOpt.get();

        // Generate token with 15 minute TTL
        String token = generator.opaqueToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);

        repository.save(PasswordResetTokenEntity.create(
                UUID.randomUUID(), user.id(), hashing.hash(token), expiresAt, now));

        // Send email
        emailSender.send(user.email(), token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = Instant.now();

        // Find token by hash
        PasswordResetTokenEntity entity = repository.findByTokenHash(hashing.hash(token))
                .orElseThrow(() -> new UnauthorizedException("TOKEN_INVALID", "Reset token is invalid"));

        // Validate token is active
        if (!entity.activeAt(now)) {
            throw new UnauthorizedException("TOKEN_EXPIRED", "Reset token has expired or been used");
        }

        // Validate user still exists and is active
        AuthUserView user = userLookupService.findAuthUserById(entity.getUserId())
                .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "User not found"));

        // Validate new password policy
        passwordService.validatePolicy(newPassword);

        // Hash new password
        String newHash = passwordService.encode(newPassword);

        // Update password
        userPasswordService.updatePassword(entity.getUserId(), newHash);

        // Mark token as consumed
        entity.consume(now);

        // Revoke ALL user sessions
        sessionRepository.revokeAllByUserId(entity.getUserId(), "PASSWORD_RESET", now);
    }
}
