# Change Password Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho phép user tự đổi mật khẩu (password user) hoặc đặt mật khẩu lần đầu qua email (OAuth user), revoke tất cả session khác sau khi thành công.

**Architecture:** Hai luồng riêng biệt trong auth module — luồng A dùng `currentPassword` verify trực tiếp; luồng B dùng token opaque gửi qua email (reuse `PasswordResetTokenEntity` với thêm column `purpose`). FE thêm trang `/settings/security` render có điều kiện dựa vào `hasPassword` field mới trong `UserProfileResponse`.

**Tech Stack:** Java 21, Spring Boot 3.5, JPA/Hibernate, Flyway, Thymeleaf (email), Next.js 14 App Router, React Hook Form, Zod, TypeScript.

## Global Constraints

- Migration file phải là `V34__...` — `V33` là migration mới nhất hiện tại.
- Không thêm DB foreign key.
- `ddl-auto` là `validate` — mọi schema change phải qua Flyway migration.
- Không store raw token trong DB — chỉ store hash (HmacSHA256 qua `TokenHashing.hash()`).
- FE dùng `react-hook-form` + `zod` theo pattern của `ResetPasswordForm.tsx`.
- FE API client dùng `apiFetch` từ `@/lib/api/client`.
- Tất cả text UI bằng tiếng Việt.

---

## File Map

### Backend — tạo mới
- `backend/app/src/main/resources/db/migration/V34__password_reset_token_add_purpose.sql`
- `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/ChangePasswordRequest.java`
- `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SetPasswordRequest.java`
- `backend/auth/src/main/java/com/fuoverflow/auth/application/ChangePasswordService.java`
- `backend/auth/src/main/java/com/fuoverflow/auth/application/SetPasswordService.java`
- `backend/auth/src/main/java/com/fuoverflow/auth/application/SetPasswordEmailSender.java`
- `backend/auth/src/main/resources/templates/mail/set-password.html`
- `backend/auth/src/test/java/com/fuoverflow/auth/application/ChangePasswordServiceTest.java`
- `backend/auth/src/test/java/com/fuoverflow/auth/application/SetPasswordServiceTest.java`

### Backend — sửa
- `backend/auth/src/main/java/com/fuoverflow/auth/persistence/PasswordResetTokenEntity.java` — thêm `purpose` field + `createSetPassword()`
- `backend/auth/src/main/java/com/fuoverflow/auth/persistence/PasswordResetTokenRepository.java` — thêm 2 queries mới
- `backend/auth/src/main/java/com/fuoverflow/auth/persistence/UserSessionRepository.java` — thêm `revokeAllExcept()`
- `backend/auth/src/main/java/com/fuoverflow/auth/api/AuthController.java` — thêm 3 endpoints
- `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java` — permit `/api/v1/auth/password/set`
- `backend/user/src/main/java/com/fuoverflow/user/api/dto/UserProfileResponse.java` — thêm `hasPassword`
- `backend/user/src/main/java/com/fuoverflow/user/persistence/UserMapper.java` — map `hasPassword`

### Frontend — tạo mới
- `Fuexam/app/(app)/settings/security/page.tsx`
- `Fuexam/app/(app)/settings/security/set-password/page.tsx`
- `Fuexam/components/auth/ChangePasswordForm.tsx`
- `Fuexam/components/auth/SetPasswordRequestCard.tsx`
- `Fuexam/components/auth/SetPasswordForm.tsx`

### Frontend — sửa
- `Fuexam/types/api.ts` — thêm `hasPassword: boolean` vào `UserProfileResponse`
- `Fuexam/lib/api/auth.ts` — thêm 3 functions mới
- `Fuexam/lib/schemas/auth.ts` — thêm 2 schemas mới

---

## Task 1: Migration + Entity + Repository

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V34__password_reset_token_add_purpose.sql`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/persistence/PasswordResetTokenEntity.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/persistence/PasswordResetTokenRepository.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/persistence/UserSessionRepository.java`

**Interfaces:**
- Produces:
  - `PasswordResetTokenEntity.createSetPassword(UUID id, UUID userId, String hash, Instant expiresAt, Instant now)` — static factory
  - `PasswordResetTokenEntity.getPurpose()` → `String`
  - `PasswordResetTokenRepository.findByTokenHashAndPurpose(String tokenHash, String purpose)` → `Optional<PasswordResetTokenEntity>`
  - `PasswordResetTokenRepository.consumeActiveByUserIdAndPurpose(UUID userId, String purpose, Instant now)` → `int`
  - `UserSessionRepository.revokeAllExcept(UUID userId, UUID excludeSessionId, String reason, Instant now)` → `int`

- [ ] **Step 1: Tạo migration file**

```sql
-- backend/app/src/main/resources/db/migration/V34__password_reset_token_add_purpose.sql
ALTER TABLE password_reset_tokens
    ADD COLUMN purpose VARCHAR(32) NOT NULL DEFAULT 'RESET_PASSWORD';
```

- [ ] **Step 2: Thêm `purpose` field vào `PasswordResetTokenEntity`**

Mở file `backend/auth/src/main/java/com/fuoverflow/auth/persistence/PasswordResetTokenEntity.java`.

Thêm field và factory method sau `createdAt`:

```java
@Column(name = "purpose", nullable = false)
private String purpose = "RESET_PASSWORD";
```

Thêm factory method `createSetPassword` sau `create`:

```java
public static PasswordResetTokenEntity createSetPassword(UUID id, UUID userId, String hash, Instant expiresAt, Instant now) {
    PasswordResetTokenEntity entity = new PasswordResetTokenEntity();
    entity.id = id;
    entity.userId = userId;
    entity.tokenHash = hash;
    entity.expiresAt = expiresAt;
    entity.createdAt = now;
    entity.purpose = "SET_PASSWORD";
    return entity;
}
```

Thêm getter:

```java
public String getPurpose() { return purpose; }
```

- [ ] **Step 3: Thêm queries vào `PasswordResetTokenRepository`**

Mở `backend/auth/src/main/java/com/fuoverflow/auth/persistence/PasswordResetTokenRepository.java`.

Thêm 2 methods:

```java
Optional<PasswordResetTokenEntity> findByTokenHashAndPurpose(String tokenHash, String purpose);

@Modifying
@Query("""
        update PasswordResetTokenEntity t
        set t.consumedAt = :now
        where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null
        """)
int consumeActiveByUserIdAndPurpose(UUID userId, String purpose, Instant now);
```

- [ ] **Step 4: Thêm `revokeAllExcept` vào `UserSessionRepository`**

Mở `backend/auth/src/main/java/com/fuoverflow/auth/persistence/UserSessionRepository.java`.

Thêm method:

```java
@Modifying
@Query("update UserSessionEntity s set s.revokedAt = :now, s.revokedReason = :reason where s.userId = :userId and s.id != :excludeSessionId and s.revokedAt is null")
int revokeAllExcept(UUID userId, UUID excludeSessionId, String reason, Instant now);
```

- [ ] **Step 5: Build để validate migration + compile**

```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS (không có compile error, Flyway sẽ apply migration khi start).

- [ ] **Step 6: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V34__password_reset_token_add_purpose.sql \
        backend/auth/src/main/java/com/fuoverflow/auth/persistence/PasswordResetTokenEntity.java \
        backend/auth/src/main/java/com/fuoverflow/auth/persistence/PasswordResetTokenRepository.java \
        backend/auth/src/main/java/com/fuoverflow/auth/persistence/UserSessionRepository.java
git commit -m "feat(auth): add purpose field to password_reset_tokens + revokeAllExcept session query"
```

---

## Task 2: `UserProfileResponse` — thêm `hasPassword`

**Files:**
- Modify: `backend/user/src/main/java/com/fuoverflow/user/api/dto/UserProfileResponse.java`
- Modify: `backend/user/src/main/java/com/fuoverflow/user/persistence/UserMapper.java`

**Interfaces:**
- Consumes: `UserEntity.getPasswordHash()` → `String` (nullable)
- Produces: `UserProfileResponse.hasPassword()` → `boolean`

- [ ] **Step 1: Thêm `hasPassword` vào record `UserProfileResponse`**

Mở `backend/user/src/main/java/com/fuoverflow/user/api/dto/UserProfileResponse.java`.

Thay toàn bộ record thành:

```java
public record UserProfileResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        String firstName,
        String lastName,
        String avatarUrl,
        UserStatus status,
        List<String> roles,
        long permVersion,
        List<String> permissions,
        boolean superAdmin,
        boolean emailVerified,
        boolean hasPassword,
        Instant createdAt
) {
}
```

- [ ] **Step 2: Cập nhật `UserMapper.toProfile()`**

Mở `backend/user/src/main/java/com/fuoverflow/user/persistence/UserMapper.java`.

Thêm `entity.getPasswordHash() != null` vào call `new UserProfileResponse(...)`. Tìm method `toProfile` và thay:

```java
public UserProfileResponse toProfile(UserEntity entity) {
    EffectivePermissions effective = permissionResolver.resolve(entity.getId());
    return new UserProfileResponse(
            entity.getId(),
            entity.getEmail(),
            entity.getUsername(),
            entity.getDisplayName(),
            entity.getFirstName(),
            entity.getLastName(),
            entity.getAvatarUrl(),
            entity.getStatus(),
            effective.roles(),
            effective.permVersion(),
            effective.permissions(),
            effective.superAdmin(),
            entity.getEmailVerifiedAt() != null,
            entity.getPasswordHash() != null,
            entity.getCreatedAt()
    );
}
```

- [ ] **Step 3: Build**

```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/user/src/main/java/com/fuoverflow/user/api/dto/UserProfileResponse.java \
        backend/user/src/main/java/com/fuoverflow/user/persistence/UserMapper.java
git commit -m "feat(user): add hasPassword field to UserProfileResponse"
```

---

## Task 3: `ChangePasswordService` + email template set-password

**Files:**
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/ChangePasswordRequest.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SetPasswordRequest.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/application/ChangePasswordService.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/application/SetPasswordEmailSender.java`
- Create: `backend/auth/src/main/resources/templates/mail/set-password.html`

**Interfaces:**
- Consumes:
  - `UserLookupService.findAuthUserById(UUID)` → `Optional<AuthUserView>`
  - `PasswordService.matches(String raw, String encoded)` → `boolean`
  - `PasswordService.encode(String raw)` → `String`
  - `PasswordService.validatePolicy(String)` — throws `BadRequestException` nếu vi phạm
  - `UserPasswordService.updatePassword(UUID userId, String passwordHash, Instant at)`
  - `UserSessionRepository.revokeAllExcept(UUID userId, UUID excludeSessionId, String reason, Instant now)`
  - `TokenGenerator.opaqueToken()` → `String`
  - `TokenHashing.hash(String)` → `String`
  - `PasswordResetTokenEntity.createSetPassword(UUID, UUID, String, Instant, Instant)`
  - `PasswordResetTokenRepository.consumeActiveByUserIdAndPurpose(UUID, String, Instant)`
  - `PasswordResetTokenRepository.save(PasswordResetTokenEntity)`
  - `ResendRateLimiter.checkAndRecord(String type, UUID userId)`
  - `OAuth2Properties.frontendBaseUrl()` → `String`
- Produces:
  - `ChangePasswordService.changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword)`
  - `SetPasswordEmailSender.send(String email, String displayName, String token)`

- [ ] **Step 1: Tạo `ChangePasswordRequest` DTO**

```java
// backend/auth/src/main/java/com/fuoverflow/auth/api/dto/ChangePasswordRequest.java
package com.fuoverflow.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
```

- [ ] **Step 2: Tạo `SetPasswordRequest` DTO**

```java
// backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SetPasswordRequest.java
package com.fuoverflow.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 128) String password
) {}
```

- [ ] **Step 3: Tạo `ChangePasswordService`**

```java
// backend/auth/src/main/java/com/fuoverflow/auth/application/ChangePasswordService.java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserPasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ChangePasswordService {
    private final UserLookupService users;
    private final UserPasswordService userPasswords;
    private final UserSessionRepository sessions;
    private final PasswordService passwords;

    public ChangePasswordService(UserLookupService users,
                                  UserPasswordService userPasswords,
                                  UserSessionRepository sessions,
                                  PasswordService passwords) {
        this.users = users;
        this.userPasswords = userPasswords;
        this.sessions = sessions;
        this.passwords = passwords;
    }

    @Transactional
    public void changePassword(UUID userId, UUID currentSessionId, String currentPassword, String newPassword) {
        var user = users.findAuthUserById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        if (user.passwordHash() == null) {
            throw new BadRequestException("NO_PASSWORD_TO_CHANGE",
                    "Tài khoản này chưa có mật khẩu. Hãy sử dụng tính năng đặt mật khẩu.");
        }
        if (!passwords.matches(currentPassword, user.passwordHash())) {
            throw new BadRequestException("WRONG_PASSWORD", "Mật khẩu hiện tại không đúng.");
        }
        passwords.validatePolicy(newPassword);
        Instant now = Instant.now();
        userPasswords.updatePassword(userId, passwords.encode(newPassword), now);
        sessions.revokeAllExcept(userId, currentSessionId, "CHANGE_PASSWORD", now);
    }
}
```

- [ ] **Step 4: Tạo `SetPasswordEmailSender`**

```java
// backend/auth/src/main/java/com/fuoverflow/auth/application/SetPasswordEmailSender.java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.config.OAuth2Properties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class SetPasswordEmailSender {
    private static final String TEMPLATE = "mail/set-password";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AuthProperties authProperties;
    private final OAuth2Properties oauth2Properties;

    public SetPasswordEmailSender(JavaMailSender mailSender,
                                   TemplateEngine templateEngine,
                                   AuthProperties authProperties,
                                   OAuth2Properties oauth2Properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.authProperties = authProperties;
        this.oauth2Properties = oauth2Properties;
    }

    @Async
    public void send(String email, String displayName, String token) {
        AuthProperties.PasswordReset config = authProperties.passwordReset();
        if (config == null || !config.enabled()) {
            return;
        }
        if (!StringUtils.hasText(config.from())) {
            throw new IllegalStateException("Mail sender address is not configured.");
        }
        String setLink = oauth2Properties.frontendBaseUrl()
                + "/settings/security/set-password?token=" + token;
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            message.setFrom(config.from());
            message.setTo(email);
            message.setSubject("Đặt mật khẩu cho tài khoản Fuexam của bạn");
            message.setText(renderHtml(displayName, setLink), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to create set-password email", e);
        } catch (MailException e) {
            throw new IllegalStateException("Failed to send set-password email", e);
        }
    }

    private String renderHtml(String displayName, String setLink) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("displayName", StringUtils.hasText(displayName) ? displayName : "Fuexam user");
        context.setVariable("setLink", setLink);
        return templateEngine.process(TEMPLATE, context);
    }
}
```

- [ ] **Step 5: Tạo email template `set-password.html`**

```html
<!-- backend/auth/src/main/resources/templates/mail/set-password.html -->
<!doctype html>
<html lang="vi" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đặt mật khẩu Fuexam</title>
</head>
<body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f4f6f8;padding:32px 0;">
    <tr>
        <td align="center">
            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;">
                <tr>
                    <td style="padding:28px 32px;background:#2563eb;color:#ffffff;">
                        <h1 style="margin:0;font-size:24px;line-height:32px;">Fuexam</h1>
                        <p style="margin:8px 0 0;font-size:15px;line-height:22px;">Đặt mật khẩu tài khoản</p>
                    </td>
                </tr>
                <tr>
                    <td style="padding:32px;">
                        <p style="margin:0 0 16px;font-size:16px;line-height:24px;">
                            Xin chào <span th:text="${displayName}">Fuexam user</span>,
                        </p>
                        <p style="margin:0 0 20px;font-size:16px;line-height:24px;">
                            Bạn đã yêu cầu đặt mật khẩu cho tài khoản Fuexam đăng nhập qua Google.
                            Nhấn vào nút bên dưới để tiếp tục.
                        </p>
                        <p style="margin:0 0 24px;">
                            <a th:href="${setLink}" href="#"
                               style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;font-weight:bold;padding:12px 20px;border-radius:8px;font-size:16px;">
                                Đặt mật khẩu
                            </a>
                        </p>
                        <p style="margin:0;font-size:14px;line-height:22px;color:#6b7280;">
                            Liên kết này có hiệu lực trong 1 giờ.
                            Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email này.
                        </p>
                    </td>
                </tr>
                <tr>
                    <td style="padding:20px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;color:#6b7280;font-size:12px;line-height:18px;">
                        Email này được gửi tự động bởi Fuexam. Vui lòng không trả lời.
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>
```

- [ ] **Step 6: Build**

```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/api/dto/ChangePasswordRequest.java \
        backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SetPasswordRequest.java \
        backend/auth/src/main/java/com/fuoverflow/auth/application/ChangePasswordService.java \
        backend/auth/src/main/java/com/fuoverflow/auth/application/SetPasswordEmailSender.java \
        backend/auth/src/main/resources/templates/mail/set-password.html
git commit -m "feat(auth): add ChangePasswordService, SetPasswordEmailSender, DTOs, email template"
```

---

## Task 4: `SetPasswordService`

**Files:**
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/application/SetPasswordService.java`

**Interfaces:**
- Consumes:
  - `UserLookupService.findAuthUserById(UUID)` → `Optional<AuthUserView>`
  - `PasswordResetTokenRepository.findByTokenHashAndPurpose(String, String)` → `Optional<PasswordResetTokenEntity>`
  - `PasswordResetTokenRepository.consumeActiveByUserIdAndPurpose(UUID, String, Instant)`
  - `PasswordResetTokenEntity.activeAt(Instant)` → `boolean`
  - `PasswordResetTokenEntity.getUserId()` → `UUID`
  - `PasswordResetTokenEntity.consume(Instant)`
  - `PasswordResetTokenEntity.createSetPassword(UUID, UUID, String, Instant, Instant)`
  - `PasswordResetTokenRepository.save(PasswordResetTokenEntity)`
  - `TokenGenerator.opaqueToken()` → `String`
  - `TokenHashing.hash(String)` → `String`
  - `UserPasswordService.updatePassword(UUID, String, Instant)`
  - `UserSessionRepository.revokeAllByUserId(UUID, String, Instant)`
  - `ResendRateLimiter.checkAndRecord(String, UUID)`
  - `SetPasswordEmailSender.send(String, String, String)`
  - `AuthProperties.passwordReset().tokenTtl()` → `Duration`
- Produces:
  - `SetPasswordService.requestSetPassword(UUID userId)` — gửi email
  - `SetPasswordService.confirmSetPassword(String token, String newPassword)` — validate + set password

- [ ] **Step 1: Tạo `SetPasswordService`**

```java
// backend/auth/src/main/java/com/fuoverflow/auth/application/SetPasswordService.java
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
```

- [ ] **Step 2: Build**

```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/application/SetPasswordService.java
git commit -m "feat(auth): add SetPasswordService (OAuth user set password via email token)"
```

---

## Task 5: Controller endpoints + SecurityConfig

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/api/AuthController.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`

**Interfaces:**
- Consumes:
  - `ChangePasswordService.changePassword(UUID, UUID, String, String)`
  - `SetPasswordService.requestSetPassword(UUID)`
  - `SetPasswordService.confirmSetPassword(String, String)`
  - `authentication.getName()` → `String` (userId UUID)
  - JWT claim `sid` → session UUID (dùng `JwtService` đã có hoặc parse từ `authentication`)

- [ ] **Step 1: Kiểm tra cách lấy `sid` từ JWT trong controller hiện tại**

Mở `AuthController.java` và kiểm tra. JWT `sid` claim là session UUID. Authentication object là `JwtAuthenticationToken` từ Spring Security OAuth2 Resource Server — có thể lấy qua:

```java
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
// ...
JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
UUID sessionId = UUID.fromString(jwtAuth.getToken().getClaimAsString("sid"));
```

- [ ] **Step 2: Thêm `ChangePasswordService` và `SetPasswordService` vào `AuthController`**

Mở `backend/auth/src/main/java/com/fuoverflow/auth/api/AuthController.java`.

Thêm 2 fields vào constructor và class:

```java
private final ChangePasswordService changePasswordService;
private final SetPasswordService setPasswordService;
```

Cập nhật constructor để inject 2 service mới (thêm vào cuối danh sách params hiện có):

```java
public AuthController(
        AuthService authService,
        CompletePendingProfileService completePendingProfileService,
        EmailVerificationService emailVerificationService,
        PasswordResetService passwordResetService,
        ChangePasswordService changePasswordService,
        SetPasswordService setPasswordService,
        CookieService cookieService,
        AuthProperties authProperties,
        CorsProperties corsProperties) {
    this.authService = authService;
    this.completePendingProfileService = completePendingProfileService;
    this.emailVerificationService = emailVerificationService;
    this.passwordResetService = passwordResetService;
    this.changePasswordService = changePasswordService;
    this.setPasswordService = setPasswordService;
    this.cookieService = cookieService;
    this.authProperties = authProperties;
    this.corsProperties = corsProperties;
}
```

- [ ] **Step 3: Thêm 3 endpoint methods vào `AuthController`**

Thêm import:

```java
import com.fuoverflow.auth.api.dto.ChangePasswordRequest;
import com.fuoverflow.auth.api.dto.SetPasswordRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
```

Thêm 3 methods sau endpoint `resetPassword`:

```java
@PatchMapping("/users/me/password")
@ResponseStatus(HttpStatus.NO_CONTENT)
@RequestMapping(path = "/api/v1/users/me/password", method = RequestMethod.PATCH)
public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                            Authentication authentication) {
    UUID userId = UUID.fromString(authentication.getName());
    UUID sessionId = UUID.fromString(
            ((JwtAuthenticationToken) authentication).getToken().getClaimAsString("sid"));
    changePasswordService.changePassword(userId, sessionId, request.currentPassword(), request.newPassword());
}

@PostMapping("/password/set-request")
public ApiResponse<Void> requestSetPassword(Authentication authentication) {
    UUID userId = UUID.fromString(authentication.getName());
    setPasswordService.requestSetPassword(userId);
    return ApiResponse.ok(null);
}

@PostMapping("/password/set")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void confirmSetPassword(@Valid @RequestBody SetPasswordRequest request) {
    setPasswordService.confirmSetPassword(request.token(), request.password());
}
```

**Lưu ý:** Endpoint `PATCH /api/v1/users/me/password` nằm ngoài prefix `/api/v1/auth` của `AuthController`. Cần tạo một controller riêng hoặc dùng `@RequestMapping` tại method level. Cách đơn giản nhất: tạo `UserPasswordController` mới trong auth module:

```java
// backend/auth/src/main/java/com/fuoverflow/auth/api/UserPasswordController.java
package com.fuoverflow.auth.api;

import com.fuoverflow.auth.api.dto.ChangePasswordRequest;
import com.fuoverflow.auth.application.ChangePasswordService;
import com.fuoverflow.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserPasswordController {
    private final ChangePasswordService changePasswordService;

    public UserPasswordController(ChangePasswordService changePasswordService) {
        this.changePasswordService = changePasswordService;
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        UUID sessionId = UUID.fromString(
                ((JwtAuthenticationToken) authentication).getToken().getClaimAsString("sid"));
        changePasswordService.changePassword(userId, sessionId, request.currentPassword(), request.newPassword());
    }
}
```

Và trong `AuthController`, chỉ thêm 2 endpoints `set-request` và `set` (dưới prefix `/api/v1/auth`):

```java
@PostMapping("/password/set-request")
public ApiResponse<Void> requestSetPassword(Authentication authentication) {
    UUID userId = UUID.fromString(authentication.getName());
    setPasswordService.requestSetPassword(userId);
    return ApiResponse.ok(null);
}

@PostMapping("/password/set")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void confirmSetPassword(@Valid @RequestBody SetPasswordRequest request) {
    setPasswordService.confirmSetPassword(request.token(), request.password());
}
```

- [ ] **Step 4: Cập nhật `SecurityConfig` — permit `/api/v1/auth/password/set`**

Mở `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`.

Tìm block `permitAll()` và thêm `"/api/v1/auth/password/set"` vào danh sách:

```java
.requestMatchers(
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/email/verify",
        "/api/v1/auth/email/resend",
        "/api/v1/auth/password/forgot",
        "/api/v1/auth/password/reset",
        "/api/v1/auth/password/set",   // <-- thêm dòng này
        "/api/v1/auth/introspect",
        "/oauth2/authorization/google",
        "/login/oauth2/code/google",
        "/api/v1/payment/payos/webhook",
        "/actuator/health",
        "/actuator/health/**"
).permitAll()
```

- [ ] **Step 5: Build**

```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/api/AuthController.java \
        backend/auth/src/main/java/com/fuoverflow/auth/api/UserPasswordController.java \
        backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java
git commit -m "feat(auth): add change-password and set-password endpoints"
```

---

## Task 6: Unit tests — Backend

**Files:**
- Create: `backend/auth/src/test/java/com/fuoverflow/auth/application/ChangePasswordServiceTest.java`
- Create: `backend/auth/src/test/java/com/fuoverflow/auth/application/SetPasswordServiceTest.java`

**Interfaces:**
- Consumes: tất cả interfaces đã define ở Task 3, 4

- [ ] **Step 1: Tạo `ChangePasswordServiceTest`**

```java
// backend/auth/src/test/java/com/fuoverflow/auth/application/ChangePasswordServiceTest.java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.persistence.UserSessionRepository;
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
                .hasMessageContaining("NO_PASSWORD_TO_CHANGE");
    }

    @Test
    void changePassword_throwsWhenWrongCurrentPassword() {
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(userWithPassword));
        when(passwords.matches("wrongPass", "hashed_pass")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(userId, sessionId, "wrongPass", "newPass123"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("WRONG_PASSWORD");
    }
}
```

- [ ] **Step 2: Run test `ChangePasswordServiceTest`**

```bash
cd backend && mvn -q -pl auth test -Dtest=ChangePasswordServiceTest
```

Expected: Tests run: 3, Failures: 0, Errors: 0.

- [ ] **Step 3: Tạo `SetPasswordServiceTest`**

```java
// backend/auth/src/test/java/com/fuoverflow/auth/application/SetPasswordServiceTest.java
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

        AuthProperties.PasswordReset pwReset = mock(AuthProperties.PasswordReset.class);
        when(pwReset.tokenTtl()).thenReturn(Duration.ofHours(1));
        when(properties.passwordReset()).thenReturn(pwReset);
    }

    @Test
    void requestSetPassword_success() {
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
                .hasMessageContaining("PASSWORD_ALREADY_SET");
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
                .hasMessageContaining("TOKEN_INVALID");
    }
}
```

- [ ] **Step 4: Run test `SetPasswordServiceTest`**

```bash
cd backend && mvn -q -pl auth test -Dtest=SetPasswordServiceTest
```

Expected: Tests run: 4, Failures: 0, Errors: 0.

- [ ] **Step 5: Run toàn bộ auth tests**

```bash
cd backend && mvn -q -pl auth test
```

Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/auth/src/test/java/com/fuoverflow/auth/application/ChangePasswordServiceTest.java \
        backend/auth/src/test/java/com/fuoverflow/auth/application/SetPasswordServiceTest.java
git commit -m "test(auth): add unit tests for ChangePasswordService and SetPasswordService"
```

---

## Task 7: Frontend — types, API client, schemas

**Files:**
- Modify: `Fuexam/types/api.ts`
- Modify: `Fuexam/lib/api/auth.ts`
- Modify: `Fuexam/lib/schemas/auth.ts`

**Interfaces:**
- Produces:
  - `UserProfileResponse.hasPassword: boolean`
  - `authApi.changePassword(currentPassword: string, newPassword: string): Promise<void>`
  - `authApi.requestSetPassword(): Promise<{ message: string }>`
  - `authApi.setPassword(token: string, password: string): Promise<void>`
  - `changePasswordSchema` — zod schema
  - `setPasswordSchema` — zod schema (reuse `resetPasswordSchema` pattern)

- [ ] **Step 1: Thêm `hasPassword` vào `UserProfileResponse` trong `types/api.ts`**

Mở `Fuexam/types/api.ts`. Tìm `interface UserProfileResponse` và thêm field:

```ts
export interface UserProfileResponse {
  id: string;
  email: string;
  username: string;
  displayName: string;
  firstName: string | null;
  lastName: string | null;
  avatarUrl: string | null;
  status: string;
  roles: string[];
  permVersion: number;
  permissions: string[];
  superAdmin: boolean;
  emailVerified: boolean;
  hasPassword: boolean;   // <-- thêm dòng này
  createdAt: string;
}
```

- [ ] **Step 2: Thêm 3 functions vào `lib/api/auth.ts`**

Mở `Fuexam/lib/api/auth.ts` và thêm vào cuối file:

```ts
export function changePassword(currentPassword: string, newPassword: string) {
  return apiFetch<void>(`${API_V1}/users/me/password`, {
    method: "PATCH",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

export function requestSetPassword() {
  return apiFetch<{ message: string }>(`${API_V1}/auth/password/set-request`, {
    method: "POST",
  });
}

export function setPassword(token: string, password: string) {
  return apiFetch<void>(`${API_V1}/auth/password/set`, {
    method: "POST",
    body: JSON.stringify({ token, password }),
  });
}
```

- [ ] **Step 3: Thêm schemas vào `lib/schemas/auth.ts`**

Mở `Fuexam/lib/schemas/auth.ts` và thêm vào cuối file:

```ts
export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Không được để trống"),
    newPassword: z.string().min(8, "Tối thiểu 8 ký tự").max(128, "Tối đa 128 ký tự"),
    confirmNewPassword: z.string(),
  })
  .refine((d) => d.newPassword === d.confirmNewPassword, {
    message: "Mật khẩu xác nhận không khớp",
    path: ["confirmNewPassword"],
  });
export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>;

export const setPasswordSchema = z
  .object({
    password: z.string().min(8, "Tối thiểu 8 ký tự").max(128, "Tối đa 128 ký tự"),
    confirmPassword: z.string(),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: "Mật khẩu xác nhận không khớp",
    path: ["confirmPassword"],
  });
export type SetPasswordFormValues = z.infer<typeof setPasswordSchema>;
```

- [ ] **Step 4: Commit**

```bash
git add Fuexam/types/api.ts Fuexam/lib/api/auth.ts Fuexam/lib/schemas/auth.ts
git commit -m "feat(fe): add hasPassword type, changePassword/setPassword API functions, Zod schemas"
```

---

## Task 8: Frontend — Components

**Files:**
- Create: `Fuexam/components/auth/ChangePasswordForm.tsx`
- Create: `Fuexam/components/auth/SetPasswordRequestCard.tsx`
- Create: `Fuexam/components/auth/SetPasswordForm.tsx`

**Interfaces:**
- Consumes:
  - `authApi.changePassword(currentPassword, newPassword)` → `Promise<void>`
  - `authApi.requestSetPassword()` → `Promise<{ message: string }>`
  - `authApi.setPassword(token, password)` → `Promise<void>`
  - `changePasswordSchema`, `ChangePasswordFormValues` từ `@/lib/schemas/auth`
  - `setPasswordSchema`, `SetPasswordFormValues` từ `@/lib/schemas/auth`
  - `PasswordInput` từ `@/components/auth/PasswordInput`
  - `ErrorBanner` từ `@/components/ui/error-banner`
  - `ApiError` từ `@/lib/api/client`

- [ ] **Step 1: Tạo `ChangePasswordForm.tsx`**

```tsx
// Fuexam/components/auth/ChangePasswordForm.tsx
"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { changePasswordSchema, type ChangePasswordFormValues } from "@/lib/schemas/auth";
import * as authApi from "@/lib/api/auth";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { ApiError } from "@/lib/api/client";
import { ErrorBanner } from "@/components/ui/error-banner";

export function ChangePasswordForm() {
  const [success, setSuccess] = useState(false);
  const form = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmNewPassword: "" },
  });

  async function onSubmit(values: ChangePasswordFormValues) {
    setSuccess(false);
    try {
      await authApi.changePassword(values.currentPassword, values.newPassword);
      setSuccess(true);
      form.reset();
    } catch (err) {
      const message =
        err instanceof ApiError && err.code === "WRONG_PASSWORD"
          ? "Mật khẩu hiện tại không đúng."
          : err instanceof ApiError
            ? err.message
            : "Không thể đổi mật khẩu. Vui lòng thử lại.";
      form.setError("root", { message });
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
      {success && (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
          Đổi mật khẩu thành công.
        </div>
      )}
      <ErrorBanner message={form.formState.errors.root?.message} />

      <div>
        <label htmlFor="currentPassword" className="mb-1 block text-sm font-medium text-slate-700">
          Mật khẩu hiện tại
        </label>
        <PasswordInput id="currentPassword" autoComplete="current-password" {...form.register("currentPassword")} />
        {form.formState.errors.currentPassword && (
          <p className="mt-1 text-xs text-red-600">{form.formState.errors.currentPassword.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="newPassword" className="mb-1 block text-sm font-medium text-slate-700">
          Mật khẩu mới
        </label>
        <PasswordInput id="newPassword" autoComplete="new-password" {...form.register("newPassword")} />
        {form.formState.errors.newPassword && (
          <p className="mt-1 text-xs text-red-600">{form.formState.errors.newPassword.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="confirmNewPassword" className="mb-1 block text-sm font-medium text-slate-700">
          Xác nhận mật khẩu mới
        </label>
        <PasswordInput id="confirmNewPassword" autoComplete="new-password" {...form.register("confirmNewPassword")} />
        {form.formState.errors.confirmNewPassword && (
          <p className="mt-1 text-xs text-red-600">{form.formState.errors.confirmNewPassword.message}</p>
        )}
      </div>

      <button type="submit" disabled={form.formState.isSubmitting} className="btn-primary w-full">
        {form.formState.isSubmitting ? "Đang cập nhật..." : "Đổi mật khẩu"}
      </button>
    </form>
  );
}
```

- [ ] **Step 2: Tạo `SetPasswordRequestCard.tsx`**

```tsx
// Fuexam/components/auth/SetPasswordRequestCard.tsx
"use client";

import { useState } from "react";
import * as authApi from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";

export function SetPasswordRequestCard() {
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleRequest() {
    setLoading(true);
    setError(null);
    try {
      await authApi.requestSetPassword();
      setSent(true);
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : "Không thể gửi email. Vui lòng thử lại."
      );
    } finally {
      setLoading(false);
    }
  }

  if (sent) {
    return (
      <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
        Kiểm tra hộp thư của bạn — chúng tôi đã gửi link đặt mật khẩu.
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <p className="text-sm text-slate-600">
        Tài khoản của bạn đăng nhập qua Google. Bạn có thể đặt mật khẩu để đăng nhập trực tiếp bằng email.
      </p>
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}
      <button onClick={handleRequest} disabled={loading} className="btn-primary">
        {loading ? "Đang gửi..." : "Gửi email xác nhận"}
      </button>
    </div>
  );
}
```

- [ ] **Step 3: Tạo `SetPasswordForm.tsx`**

```tsx
// Fuexam/components/auth/SetPasswordForm.tsx
"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { setPasswordSchema, type SetPasswordFormValues } from "@/lib/schemas/auth";
import * as authApi from "@/lib/api/auth";
import { PasswordInput } from "@/components/auth/PasswordInput";
import { ApiError } from "@/lib/api/client";
import { ErrorBanner } from "@/components/ui/error-banner";

interface Props {
  token: string;
}

export function SetPasswordForm({ token }: Props) {
  const router = useRouter();
  const [tokenInvalid, setTokenInvalid] = useState(false);
  const form = useForm<SetPasswordFormValues>({
    resolver: zodResolver(setPasswordSchema),
    defaultValues: { password: "", confirmPassword: "" },
  });

  async function onSubmit(values: SetPasswordFormValues) {
    try {
      await authApi.setPassword(token, values.password);
      router.push("/settings/security?passwordSet=1");
    } catch (err) {
      if (err instanceof ApiError && (err.code === "TOKEN_INVALID" || err.code === "TOKEN_EXPIRED")) {
        setTokenInvalid(true);
        return;
      }
      form.setError("root", {
        message: err instanceof ApiError ? err.message : "Không thể đặt mật khẩu. Vui lòng thử lại.",
      });
    }
  }

  if (tokenInvalid) {
    return (
      <div className="space-y-4">
        <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          Liên kết đặt mật khẩu không hợp lệ hoặc đã hết hạn.
        </div>
        <Link href="/settings/security" className="btn-primary inline-block w-full text-center">
          Gửi lại email xác nhận
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
      <ErrorBanner message={form.formState.errors.root?.message} />

      <div>
        <label htmlFor="password" className="mb-1 block text-sm font-medium text-slate-700">
          Mật khẩu mới
        </label>
        <PasswordInput id="password" autoComplete="new-password" {...form.register("password")} />
        {form.formState.errors.password && (
          <p className="mt-1 text-xs text-red-600">{form.formState.errors.password.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="confirmPassword" className="mb-1 block text-sm font-medium text-slate-700">
          Xác nhận mật khẩu mới
        </label>
        <PasswordInput id="confirmPassword" autoComplete="new-password" {...form.register("confirmPassword")} />
        {form.formState.errors.confirmPassword && (
          <p className="mt-1 text-xs text-red-600">{form.formState.errors.confirmPassword.message}</p>
        )}
      </div>

      <button type="submit" disabled={form.formState.isSubmitting} className="btn-primary w-full">
        {form.formState.isSubmitting ? "Đang xác nhận..." : "Xác nhận mật khẩu"}
      </button>
    </form>
  );
}
```

- [ ] **Step 4: Commit**

```bash
git add Fuexam/components/auth/ChangePasswordForm.tsx \
        Fuexam/components/auth/SetPasswordRequestCard.tsx \
        Fuexam/components/auth/SetPasswordForm.tsx
git commit -m "feat(fe): add ChangePasswordForm, SetPasswordRequestCard, SetPasswordForm components"
```

---

## Task 9: Frontend — Pages

**Files:**
- Create: `Fuexam/app/(app)/settings/security/page.tsx`
- Create: `Fuexam/app/(app)/settings/security/set-password/page.tsx`

**Interfaces:**
- Consumes:
  - `useAuth()` → `{ user: UserProfileResponse | null, loading: boolean }` từ `@/lib/auth/AuthProvider`
  - `user.hasPassword: boolean`
  - `ChangePasswordForm` từ `@/components/auth/ChangePasswordForm`
  - `SetPasswordRequestCard` từ `@/components/auth/SetPasswordRequestCard`
  - `SetPasswordForm` từ `@/components/auth/SetPasswordForm`
  - `useSearchParams()` từ `next/navigation`

- [ ] **Step 1: Tạo `/settings/security/page.tsx`**

```tsx
// Fuexam/app/(app)/settings/security/page.tsx
"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ChangePasswordForm } from "@/components/auth/ChangePasswordForm";
import { SetPasswordRequestCard } from "@/components/auth/SetPasswordRequestCard";

function SecurityPageContent() {
  const { user, loading } = useAuth();
  const searchParams = useSearchParams();
  const passwordSet = searchParams.get("passwordSet") === "1";

  if (loading) {
    return <p className="text-sm text-slate-500">Đang tải...</p>;
  }

  if (!user) return null;

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <h1 className="text-xl font-bold text-slate-900">Bảo mật</h1>

      {passwordSet && (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          Đặt mật khẩu thành công. Bạn có thể đăng nhập bằng email và mật khẩu.
        </div>
      )}

      <div className="card p-6">
        <h2 className="mb-4 text-base font-semibold text-slate-900">
          {user.hasPassword ? "Đổi mật khẩu" : "Đặt mật khẩu"}
        </h2>
        {user.hasPassword ? <ChangePasswordForm /> : <SetPasswordRequestCard />}
      </div>
    </div>
  );
}

export default function SecurityPage() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Đang tải...</p>}>
      <SecurityPageContent />
    </Suspense>
  );
}
```

- [ ] **Step 2: Tạo `/settings/security/set-password/page.tsx`**

```tsx
// Fuexam/app/(app)/settings/security/set-password/page.tsx
"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect } from "react";
import { SetPasswordForm } from "@/components/auth/SetPasswordForm";

function SetPasswordContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");

  useEffect(() => {
    if (!token) {
      router.replace("/settings/security");
    }
  }, [token, router]);

  if (!token) return null;

  return (
    <div className="mx-auto max-w-md">
      <div className="card p-6">
        <h1 className="mb-6 text-xl font-bold text-slate-900">Đặt mật khẩu mới</h1>
        <SetPasswordForm token={token} />
      </div>
    </div>
  );
}

export default function SetPasswordPage() {
  return (
    <Suspense fallback={<p className="text-sm text-slate-500">Đang tải...</p>}>
      <SetPasswordContent />
    </Suspense>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add "Fuexam/app/(app)/settings/security/page.tsx" \
        "Fuexam/app/(app)/settings/security/set-password/page.tsx"
git commit -m "feat(fe): add /settings/security and /settings/security/set-password pages"
```

---

## Self-Review Checklist

### Spec coverage

| Requirement | Task |
|-------------|------|
| Migration `V34` thêm `purpose` column | Task 1 |
| `PasswordResetTokenEntity` thêm `purpose` + factory | Task 1 |
| Repository queries mới | Task 1 |
| `revokeAllExcept` trong `UserSessionRepository` | Task 1 |
| `UserProfileResponse.hasPassword` | Task 2 |
| `ChangePasswordService` — validate currentPassword, revoke sessions | Task 3, 6 |
| `SetPasswordService.requestSetPassword` — email token | Task 4 |
| `SetPasswordService.confirmSetPassword` — validate token, set password | Task 4 |
| Email template `set-password.html` | Task 3 |
| `SetPasswordEmailSender` | Task 3 |
| 3 endpoints BE | Task 5 |
| `SecurityConfig` permit `/api/v1/auth/password/set` | Task 5 |
| Unit tests BE | Task 6 |
| FE types + API client + schemas | Task 7 |
| FE components: ChangePasswordForm, SetPasswordRequestCard, SetPasswordForm | Task 8 |
| FE pages: /settings/security, /settings/security/set-password | Task 9 |
| Render có điều kiện dựa vào `hasPassword` | Task 9 |
| Revoke other sessions sau đổi mật khẩu | Task 3 (ChangePasswordService), Task 4 (SetPasswordService) |
| Rate limit cho set-request | Task 4 |
