package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.EmailVerificationTokenEntity;
import com.fuoverflow.auth.persistence.EmailVerificationTokenRepository;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.common.support.ResendRateLimiter;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserEmailVerificationService;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository repository;
    private final TokenGenerator generator;
    private final TokenHashing hashing;
    private final UserEmailVerificationService users;
    private final UserLookupService userLookup;
    private final VerificationEmailSender emailSender;
    private final ResendRateLimiter rateLimiter;
    private final AuthProperties properties;

    public EmailVerificationService(
            EmailVerificationTokenRepository repository,
            TokenGenerator generator,
            TokenHashing hashing,
            UserEmailVerificationService users,
            UserLookupService userLookup,
            VerificationEmailSender emailSender,
            ResendRateLimiter rateLimiter,
            AuthProperties properties) {
        this.repository = repository;
        this.generator = generator;
        this.hashing = hashing;
        this.users = users;
        this.userLookup = userLookup;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public record CreateVerificationResult(String token, String code) {}

    @Transactional
    public CreateVerificationResult create(UUID userId) {
        String token = generator.opaqueToken();
        String code = generator.verificationCode(codeLength());
        Instant now = Instant.now();
        repository.save(EmailVerificationTokenEntity.create(
                UUID.randomUUID(), userId,
                hashing.hash(token), hashing.hash(code),
                now.plus(codeTtl()), now, maxAttempts()));
        return new CreateVerificationResult(token, code);
    }

    @Transactional
    public AuthUserView verify(String token) {
        Instant now = Instant.now();
        EmailVerificationTokenEntity entity = repository.findByTokenHash(hashing.hash(token))
                .orElseThrow(() -> new UnauthorizedException("TOKEN_INVALID", "Verification token is invalid"));
        if (!entity.activeAt(now)) {
            throw new UnauthorizedException("TOKEN_EXPIRED", "Verification token is expired");
        }
        entity.consume(now);
        return users.markEmailVerified(entity.getUserId(), now);
    }

    @Transactional
    public AuthUserView verifyCode(String email, String code) {
        AuthUserView user = userLookup.findAuthUserByIdentifier(email)
                .orElseThrow(() -> new UnauthorizedException("CODE_INVALID", "Verification code is invalid"));

        Instant now = Instant.now();
        String codeHash = hashing.hash(code);

        Optional<EmailVerificationTokenEntity> match =
                repository.findActiveByUserIdAndCodeHash(user.id(), codeHash, now);

        if (match.isEmpty()) {
            repository.findLatestActiveByUserId(user.id(), now).ifPresent(token -> {
                token.incrementAttempts();
                if (token.isLockedOut()) {
                    token.consume(now);
                }
            });
            throw new UnauthorizedException("CODE_INVALID", "Verification code is invalid");
        }

        EmailVerificationTokenEntity entity = match.get();
        if (entity.isLockedOut()) {
            throw new UnauthorizedException("CODE_LOCKED",
                    "Too many failed attempts. Please request a new code.");
        }

        entity.consume(now);
        return users.markEmailVerified(entity.getUserId(), now);
    }

    @Transactional
    public void resend(String email) {
        AuthUserView user = userLookup.findAuthUserByIdentifier(email).orElse(null);
        if (user == null || user.emailVerified() || user.status() != UserStatus.PENDING_EMAIL_VERIFICATION) {
            return;
        }
        rateLimiter.checkAndRecord("email_verify", user.id());
        Instant now = Instant.now();
        repository.consumeActiveByUserId(user.id(), now);
        String token = generator.opaqueToken();
        String code = generator.verificationCode(codeLength());
        repository.save(EmailVerificationTokenEntity.create(
                UUID.randomUUID(), user.id(),
                hashing.hash(token), hashing.hash(code),
                now.plus(codeTtl()), now, maxAttempts()));
        emailSender.send(user.email(), user.displayName(), token, code);
    }

    private int codeLength() {
        AuthProperties.EmailVerification config = properties.emailVerification();
        return (config != null && config.codeLength() > 0) ? config.codeLength() : 6;
    }

    private Duration codeTtl() {
        AuthProperties.EmailVerification config = properties.emailVerification();
        return (config != null && config.codeTtl() != null) ? config.codeTtl() : Duration.ofMinutes(10);
    }

    private int maxAttempts() {
        AuthProperties.EmailVerification config = properties.emailVerification();
        return (config != null && config.maxAttempts() > 0) ? config.maxAttempts() : 5;
    }
}
