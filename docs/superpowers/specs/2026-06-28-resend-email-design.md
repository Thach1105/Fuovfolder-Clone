# Resend Email Flows — Design Spec

**Date:** 2026-06-28
**Status:** Approved
**Scope:** Auth module — resend email verification + resend password reset, with Redis-backed rate limiting

---

## 1. Problem

- `POST /api/v1/auth/email/verify` finalizes verification but there is no endpoint to resend the verification email if the original link expires or is lost.
- `POST /api/v1/auth/password/forgot` exists but has no rate limiting — users can spam it with no throttle.
- No shared rate limiting infrastructure exists in the project.

---

## 2. Solution Overview

1. Add **`ResendRateLimiter`** to `common` module — Redis-backed, shared by both flows.
2. Add **`POST /api/v1/auth/email/resend`** — new public endpoint to resend verification email.
3. Wire **`ResendRateLimiter`** into existing `PasswordResetService.requestReset()`.

---

## 3. Rate Limiting Design

### Redis Keys

| Key | TTL | Purpose |
|-----|-----|---------|
| `resend:cooldown:{type}:{userId}` | 120s | Cooldown between sends |
| `resend:daily:{type}:{userId}:{date}` | 24h | Daily send count (rolling 24h window keyed by UTC date) |

`{type}` values: `email_verify`, `password_reset`
`{date}` format: `yyyy-MM-dd` (UTC) — key expires 24h after first request of that UTC day

### Limits

- Cooldown: 120 seconds between requests
- Daily max: 20 requests per user per day

### Error Responses (HTTP 429)

```json
{ "code": "RESEND_TOO_SOON", "message": "Please wait 87 seconds before requesting again." }
{ "code": "RESEND_DAILY_LIMIT_EXCEEDED", "message": "Daily resend limit reached. Try again tomorrow." }
```

### `ResendRateLimiter` Interface

```java
// backend/common/.../support/ResendRateLimiter.java
@Component
public class ResendRateLimiter {
    // Checks cooldown and daily limit, then records the attempt.
    // Throws ApiException (429) on violation.
    public void checkAndRecord(String type, UUID userId);
}
```

Dependencies: `StringRedisTemplate` (auto-configured from app's Redis config).

---

## 4. Email Verification Resend

### New Endpoint

```
POST /api/v1/auth/email/resend
Content-Type: application/json
Auth: Public (no token required)

Request:  { "email": "string" }
Response: 200 OK (always — no user enumeration)
```

### Service Logic — `EmailVerificationService.resend(String email)`

```
1. Lookup user by email
   → not found: return silently (no error, no enumeration)
2. Check user.emailVerified == false
   → already verified: return silently
3. resendRateLimiter.checkAndRecord("email_verify", userId)
   → throws 429 on violation
4. consumeActiveByUserId(userId)   ← invalidate old tokens
5. create new token (24h TTL)
6. send verification email
7. return
```

### New Repository Method

```java
// EmailVerificationTokenRepository
void consumeActiveByUserId(UUID userId, Instant now);
```

Mirrors existing `PasswordResetTokenRepository.consumeActiveByUserId()`.

### New DTO

```java
// ResendVerificationRequest.java
public record ResendVerificationRequest(@Email @NotBlank String email) {}
```

---

## 5. Password Reset Rate Limiting (Update Existing)

No new endpoint. `PasswordResetService.requestReset(String email)` gains rate limiting:

```
1. (existing) Lookup user by email → not found: return silently
2. [NEW] resendRateLimiter.checkAndRecord("password_reset", userId)
   → throws 429 on violation
3. (existing) consumeActiveByUserId(userId)
4. (existing) create new token → send email
5. (existing) return
```

---

## 6. Files Changed

| File | Change |
|------|--------|
| `common/pom.xml` | Add `spring-boot-starter-data-redis` dependency |
| `common/.../support/ResendRateLimiter.java` | New class |
| `auth/.../api/AuthController.java` | Add `POST /api/v1/auth/email/resend` endpoint |
| `auth/.../api/dto/ResendVerificationRequest.java` | New DTO |
| `auth/.../application/EmailVerificationService.java` | Add `resend()` method + inject `ResendRateLimiter` |
| `auth/.../application/PasswordResetService.java` | Inject `ResendRateLimiter`, call in `requestReset()` |
| `auth/.../persistence/EmailVerificationTokenRepository.java` | Add `consumeActiveByUserId()` |
| `auth/.../config/SecurityConfig.java` | Permit `/api/v1/auth/email/resend` as public |

---

## 7. Security Considerations

- Both endpoints return generic 200 on unknown email — no user enumeration.
- Rate limiting is per-userId (not per-IP) — prevents a single account from spamming but does not protect unauthenticated enumeration at scale. Acceptable for MVP.
- Token invalidation on resend ensures only one active token per user at a time.
- 429 response reveals that a userId was found — acceptable trade-off since the user must know their own email.

---

## 8. Out of Scope

- IP-based rate limiting
- Resend for other email types (invitations, OTP)
- Email delivery status tracking
