package com.fuoverflow.auth.application;

import com.fuoverflow.auth.persistence.EmailVerificationTokenEntity;
import com.fuoverflow.auth.persistence.EmailVerificationTokenRepository;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.common.support.ResendRateLimiter;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserEmailVerificationService;
import com.fuoverflow.user.application.UserLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    public EmailVerificationService(
            EmailVerificationTokenRepository repository,
            TokenGenerator generator,
            TokenHashing hashing,
            UserEmailVerificationService users,
            UserLookupService userLookup,
            VerificationEmailSender emailSender,
            ResendRateLimiter rateLimiter) {
        this.repository = repository;
        this.generator = generator;
        this.hashing = hashing;
        this.users = users;
        this.userLookup = userLookup;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public String create(UUID userId) {
        String token = generator.opaqueToken();
        Instant now = Instant.now();
        repository.save(EmailVerificationTokenEntity.create(
                UUID.randomUUID(), userId, hashing.hash(token), now.plus(24, ChronoUnit.HOURS), now));
        return token;
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
    public void resend(String email) {
        AuthUserView user = userLookup.findAuthUserByIdentifier(email).orElse(null);
        if (user == null || user.emailVerified()) {
            return;
        }
        rateLimiter.checkAndRecord("email_verify", user.id());
        Instant now = Instant.now();
        repository.consumeActiveByUserId(user.id(), now);
        String token = generator.opaqueToken();
        repository.save(EmailVerificationTokenEntity.create(
                UUID.randomUUID(), user.id(), hashing.hash(token), now.plus(24, ChronoUnit.HOURS), now));
        emailSender.send(user.email(), user.displayName(), token);
    }
}
