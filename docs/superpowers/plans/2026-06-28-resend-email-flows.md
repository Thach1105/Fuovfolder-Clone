# Resend Email Flows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add resend email verification endpoint and wire rate limiting into forgot-password flow, backed by Redis with 120s cooldown and 20 requests/day/user limit.

**Architecture:** `ResendRateLimiter` in `common` module uses `StringRedisTemplate` (already auto-configured) with two Redis keys per user/type. `EmailVerificationService.resend()` handles the new endpoint. `PasswordResetService.requestReset()` gains rate limit check. New `TooManyRequestsException` (from error-response plan) used for 429 responses.

**Tech Stack:** Spring Boot 3.5.x, `spring-boot-starter-data-redis` (already in `app/pom.xml`), `StringRedisTemplate`, Spring Data JPA.

## Global Constraints

- Java 21, Spring Boot 3.5.x
- No new Maven dependencies — Redis already on classpath via `app`
- `ResendRateLimiter` lives in `common` module, not `auth`
- Both resend flows return generic 200 on unknown email — no user enumeration
- Cooldown: 120 seconds; Daily limit: 20 per user per day (UTC date key)
- Rate limit applies per `(type, userId)` pair — not per IP
- Token invalidation on resend: consume all active tokens before creating new one
- `TooManyRequestsException` must exist (implement Plan 1 first, or implement Task 0 below)

## Dependency

This plan requires `TooManyRequestsException` from `backend/common/src/main/java/com/fuoverflow/common/exception/TooManyRequestsException.java`. If Plan 1 (error-response-standardization) has not been implemented yet, implement Task 0 first.

---

### Task 0 (conditional): Add `TooManyRequestsException` if not already present

Skip this task if Plan 1 has already been implemented.

**Files:**
- Create: `backend/common/src/main/java/com/fuoverflow/common/exception/TooManyRequestsException.java`

- [ ] **Step 1: Check if class exists**

```bash
find backend/common/src -name "TooManyRequestsException.java"
```

If file exists → skip this task entirely.

- [ ] **Step 2: Create `TooManyRequestsException`**

```java
package com.fuoverflow.common.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String code, String message) {
        super(code, message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/common/src/main/java/com/fuoverflow/common/exception/TooManyRequestsException.java
git commit -m "feat(common): add TooManyRequestsException (429)"
```

---

### Task 1: Add `spring-boot-starter-data-redis` to `common/pom.xml`

**Files:**
- Modify: `backend/common/pom.xml`

- [ ] **Step 1: Add Redis dependency**

In `backend/common/pom.xml`, inside `<dependencies>`, add:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

- [ ] **Step 2: Verify build**

```bash
cd backend && mvn -q -DskipTests package -pl common
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/common/pom.xml
git commit -m "feat(common): add spring-boot-starter-data-redis dependency"
```

---

### Task 2: `ResendRateLimiter` in `common` module

**Files:**
- Create: `backend/common/src/main/java/com/fuoverflow/common/support/ResendRateLimiter.java`
- Create (test): `backend/common/src/test/java/com/fuoverflow/common/support/ResendRateLimiterTest.java`

**Interfaces:**
- Produces: `ResendRateLimiter.checkAndRecord(String type, UUID userId)` — void, throws `TooManyRequestsException` on violation
- Redis key `resend:cooldown:{type}:{userId}` — TTL 120s
- Redis key `resend:daily:{type}:{userId}:{date}` — TTL 24h, `{date}` = UTC `yyyy-MM-dd`

- [ ] **Step 1: Write failing tests**

Create `backend/common/src/test/java/com/fuoverflow/common/support/ResendRateLimiterTest.java`:

```java
package com.fuoverflow.common.support;

import com.fuoverflow.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResendRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private ResendRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        limiter = new ResendRateLimiter(redis);
    }

    @Test
    void shouldThrowWhenCooldownKeyExists() {
        UUID userId = UUID.randomUUID();
        when(redis.hasKey(contains("cooldown"))).thenReturn(true);

        assertThatThrownBy(() -> limiter.checkAndRecord("email_verify", userId))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> {
                    var apiEx = (TooManyRequestsException) ex;
                    assertThat(apiEx.code()).isEqualTo("RESEND_TOO_SOON");
                });
    }

    @Test
    void shouldThrowWhenDailyLimitReached() {
        UUID userId = UUID.randomUUID();
        when(redis.hasKey(contains("cooldown"))).thenReturn(false);
        when(ops.get(contains("daily"))).thenReturn("20");

        assertThatThrownBy(() -> limiter.checkAndRecord("email_verify", userId))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> {
                    var apiEx = (TooManyRequestsException) ex;
                    assertThat(apiEx.code()).isEqualTo("RESEND_DAILY_LIMIT_EXCEEDED");
                });
    }

    @Test
    void shouldRecordAttemptWhenAllowed() {
        UUID userId = UUID.randomUUID();
        when(redis.hasKey(contains("cooldown"))).thenReturn(false);
        when(ops.get(contains("daily"))).thenReturn("5");

        limiter.checkAndRecord("email_verify", userId);

        verify(ops).set(contains("cooldown"), eq("1"), eq(120L), eq(TimeUnit.SECONDS));
        verify(ops).increment(contains("daily"));
    }

    @Test
    void shouldSetDailyKeyTtlWhenCounterIsOne() {
        UUID userId = UUID.randomUUID();
        when(redis.hasKey(contains("cooldown"))).thenReturn(false);
        when(ops.get(contains("daily"))).thenReturn(null);
        when(ops.increment(contains("daily"))).thenReturn(1L);

        limiter.checkAndRecord("email_verify", userId);

        verify(redis).expire(contains("daily"), eq(24L), eq(TimeUnit.HOURS));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn -q test -pl common -Dtest=ResendRateLimiterTest
```

Expected: FAIL — `ResendRateLimiter` does not exist.

- [ ] **Step 3: Create `ResendRateLimiter`**

Create `backend/common/src/main/java/com/fuoverflow/common/support/ResendRateLimiter.java`:

```java
package com.fuoverflow.common.support;

import com.fuoverflow.common.exception.TooManyRequestsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class ResendRateLimiter {

    private static final int COOLDOWN_SECONDS = 120;
    private static final int DAILY_MAX = 20;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StringRedisTemplate redis;

    public ResendRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void checkAndRecord(String type, UUID userId) {
        String cooldownKey = cooldownKey(type, userId);
        String dailyKey = dailyKey(type, userId);

        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            Long ttl = redis.getExpire(cooldownKey, TimeUnit.SECONDS);
            long remaining = ttl != null && ttl > 0 ? ttl : COOLDOWN_SECONDS;
            throw new TooManyRequestsException("RESEND_TOO_SOON",
                    String.format("Vui lòng chờ %d giây trước khi gửi lại.", remaining));
        }

        String dailyCountStr = redis.opsForValue().get(dailyKey);
        int dailyCount = dailyCountStr != null ? Integer.parseInt(dailyCountStr) : 0;
        if (dailyCount >= DAILY_MAX) {
            throw new TooManyRequestsException("RESEND_DAILY_LIMIT_EXCEEDED",
                    "Bạn đã đạt giới hạn gửi email trong ngày hôm nay. Vui lòng thử lại vào ngày mai.");
        }

        redis.opsForValue().set(cooldownKey, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);
        Long newCount = redis.opsForValue().increment(dailyKey);
        if (newCount != null && newCount == 1) {
            redis.expire(dailyKey, 24, TimeUnit.HOURS);
        }
    }

    private String cooldownKey(String type, UUID userId) {
        return String.format("resend:cooldown:%s:%s", type, userId);
    }

    private String dailyKey(String type, UUID userId) {
        String date = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMAT);
        return String.format("resend:daily:%s:%s:%s", type, userId, date);
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd backend && mvn -q test -pl common -Dtest=ResendRateLimiterTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/common/src
git commit -m "feat(common): add ResendRateLimiter with Redis-backed cooldown and daily limit"
```

---

### Task 3: Add `consumeActiveByUserId` to `EmailVerificationTokenRepository`

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/persistence/EmailVerificationTokenRepository.java`

**Interfaces:**
- Produces: `consumeActiveByUserId(UUID userId, Instant now)` — used by Task 4

- [ ] **Step 1: Update repository**

Replace `backend/auth/src/main/java/com/fuoverflow/auth/persistence/EmailVerificationTokenRepository.java`:

```java
package com.fuoverflow.auth.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, UUID> {

    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update EmailVerificationTokenEntity t
            set t.consumedAt = :now
            where t.userId = :userId and t.consumedAt is null
            """)
    int consumeActiveByUserId(UUID userId, Instant now);
}
```

- [ ] **Step 2: Build auth module**

```bash
cd backend && mvn -q -DskipTests package -pl auth
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/persistence/EmailVerificationTokenRepository.java
git commit -m "feat(auth): add consumeActiveByUserId to EmailVerificationTokenRepository"
```

---

### Task 4: Add `resend()` to `EmailVerificationService`

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/application/EmailVerificationService.java`
- Create (test): `backend/auth/src/test/java/com/fuoverflow/auth/application/EmailVerificationServiceResendTest.java`

**Interfaces:**
- Consumes: `ResendRateLimiter.checkAndRecord(String, UUID)` from Task 2
- Consumes: `UserLookupService.findAuthUserByIdentifier(String)` → `Optional<AuthUserView>`
- Consumes: `EmailVerificationTokenRepository.consumeActiveByUserId(UUID, Instant)` from Task 3
- Consumes: `VerificationEmailSender.send(String email, String displayName, String token)`
- Produces: `EmailVerificationService.resend(String email)` — void, public

- [ ] **Step 1: Write failing test**

Create `backend/auth/src/test/java/com/fuoverflow/auth/application/EmailVerificationServiceResendTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn -q test -pl auth -Dtest=EmailVerificationServiceResendTest
```

Expected: FAIL — `EmailVerificationService` has no `resend()` method, wrong constructor.

- [ ] **Step 3: Update `EmailVerificationService`**

Replace `backend/auth/src/main/java/com/fuoverflow/auth/application/EmailVerificationService.java`:

```java
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
```

- [ ] **Step 4: Run tests**

```bash
cd backend && mvn -q test -pl auth -Dtest=EmailVerificationServiceResendTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/application/EmailVerificationService.java \
        backend/auth/src/test/java/com/fuoverflow/auth/application/EmailVerificationServiceResendTest.java
git commit -m "feat(auth): add EmailVerificationService.resend() with rate limiting"
```

---

### Task 5: Wire rate limiting into `PasswordResetService`

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/application/PasswordResetService.java`
- Create (test): `backend/auth/src/test/java/com/fuoverflow/auth/application/PasswordResetServiceResendTest.java`

**Interfaces:**
- Consumes: `ResendRateLimiter.checkAndRecord(String, UUID)` from Task 2

- [ ] **Step 1: Write failing test**

Create `backend/auth/src/test/java/com/fuoverflow/auth/application/PasswordResetServiceResendTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn -q test -pl auth -Dtest=PasswordResetServiceResendTest
```

Expected: FAIL — constructor has no `ResendRateLimiter` parameter.

- [ ] **Step 3: Update `PasswordResetService`**

Replace `backend/auth/src/main/java/com/fuoverflow/auth/application/PasswordResetService.java`:

```java
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
```

- [ ] **Step 4: Run tests**

```bash
cd backend && mvn -q test -pl auth -Dtest=PasswordResetServiceResendTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/application/PasswordResetService.java \
        backend/auth/src/test/java/com/fuoverflow/auth/application/PasswordResetServiceResendTest.java
git commit -m "feat(auth): add rate limiting to PasswordResetService.requestReset()"
```

---

### Task 6: Add `POST /api/v1/auth/email/resend` endpoint

**Files:**
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/ResendVerificationRequest.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/api/AuthController.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`

- [ ] **Step 1: Create `ResendVerificationRequest` DTO**

Create `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/ResendVerificationRequest.java`:

```java
package com.fuoverflow.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank @Email String email
) {}
```

- [ ] **Step 2: Add endpoint to `AuthController`**

In `backend/auth/src/main/java/com/fuoverflow/auth/api/AuthController.java`, add the new endpoint after `verifyEmail()`:

```java
@PostMapping("/email/resend")
public ApiResponse<Void> resendVerificationEmail(@Valid @RequestBody ResendVerificationRequest request) {
    emailVerificationService.resend(request.email());
    return ApiResponse.ok(null);
}
```

Also add the import for `ResendVerificationRequest` at the top of the file (it's in the same `dto` package so the wildcard `import com.fuoverflow.auth.api.dto.*;` already covers it).

- [ ] **Step 3: Permit new endpoint in `SecurityConfig`**

In `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`, add `"/api/v1/auth/email/resend"` to the `permitAll()` list:

```java
.requestMatchers(
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/email/verify",
        "/api/v1/auth/email/resend",          // <-- add this line
        "/api/v1/auth/password/forgot",
        "/api/v1/auth/password/reset",
        "/api/v1/auth/introspect",
        "/oauth2/authorization/google",
        "/login/oauth2/code/google",
        "/api/v1/payment/payos/webhook",
        "/actuator/health",
        "/actuator/health/**"
).permitAll()
```

- [ ] **Step 4: Build full project**

```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Run all auth tests**

```bash
cd backend && mvn -q test -pl auth
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/auth/src
git commit -m "feat(auth): add POST /api/v1/auth/email/resend endpoint"
```

---

### Task 7: Full build and integration smoke test

- [ ] **Step 1: Run full test suite**

```bash
cd backend && mvn -q test
```

Expected: BUILD SUCCESS, no failures.

- [ ] **Step 2: Start app and smoke test**

```bash
cd backend && docker compose up -d postgres redis
mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local
```

Test resend verification email (returns 200 for unknown email — no enumeration):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/email/resend \
  -H "Content-Type: application/json" \
  -d '{"email":"unknown@example.com"}' | jq .
```
Expected: `{"success":true,"code":"OK",...}`

Test rate limit on password reset (hit it twice fast):
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/password/forgot \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}' | jq .

curl -s -X POST http://localhost:8080/api/v1/auth/password/forgot \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}' | jq .
```
Expected second call: `{"success":false,"code":"RESEND_TOO_SOON",...}` (only if user exists in DB).
