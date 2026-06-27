# Error Response Standardization — Design Spec

**Date:** 2026-06-28
**Status:** Approved
**Scope:** Global — all API error responses across all modules

---

## 1. Problem

- 5xx error message (`"Unexpected server error"`) lacks contact information — users have no way to report issues.
- No `TooManyRequestsException` (429) in exception hierarchy — needed for resend rate limiting.
- `OAuthEmailNotVerifiedException` extends `RuntimeException` instead of `ApiException` → falls through to catch-all → returns 500 instead of a meaningful 4xx.

---

## 2. Current Error Response Shape

```json
{
  "success": false,
  "code": "INTERNAL_ERROR",
  "message": "Unexpected server error",
  "error": { "traceId": "abc-123", "fields": null },
  "timestamp": "2026-06-28T07:30:00Z"
}
```

---

## 3. Solution Overview

1. Add `SupportProperties` — bind support contact info from env vars.
2. Update `GlobalExceptionHandler` — inject `SupportProperties`, use in 5xx message.
3. Add `TooManyRequestsException` — new 429 exception subclass.
4. Fix `OAuthEmailNotVerifiedException` — extend `ApiException` instead of `RuntimeException`.

---

## 4. Design

### 4.1 `SupportProperties`

**File:** `backend/common/src/main/java/com/fuoverflow/common/config/SupportProperties.java`

```java
@ConfigurationProperties(prefix = "app.support")
public record SupportProperties(String email, String phone) {}
```

**`backend/app/src/main/resources/application.yml`** — thêm:
```yaml
app:
  support:
    email: ${SUPPORT_EMAIL}
    phone: ${SUPPORT_PHONE}
```

Enable via `@EnableConfigurationProperties(SupportProperties.class)` trong `common` module config hoặc `app` module.

---

### 4.2 Updated 5xx Message

**`GlobalExceptionHandler`** — catch-all `Exception` handler cập nhật message:

```
"Đã xảy ra lỗi hệ thống. Vui lòng liên hệ hỗ trợ qua email {email} hoặc số điện thoại {phone}."
```

Ví dụ output:
```json
{
  "success": false,
  "code": "INTERNAL_ERROR",
  "message": "Đã xảy ra lỗi hệ thống. Vui lòng liên hệ hỗ trợ qua email support@fuoverflow.com hoặc số điện thoại 0900-000-000.",
  "error": { "traceId": "abc-123" },
  "timestamp": "2026-06-28T14:30:00+07:00"
}
```

Stack trace vẫn được log server-side, không leak ra client.

---

### 4.3 `TooManyRequestsException` (429)

**File:** `backend/common/src/main/java/com/fuoverflow/common/exception/TooManyRequestsException.java`

```java
public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String code, String message) {
        super(code, message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
```

`GlobalExceptionHandler` đã catch `ApiException` → tự xử lý 429 không cần thêm handler mới.

---

### 4.4 Fix `OAuthEmailNotVerifiedException`

**Current:** extends `RuntimeException` → falls through to 500 catch-all.

**Fix:** extends `ForbiddenException` với code `OAUTH_EMAIL_NOT_VERIFIED`.

```java
public class OAuthEmailNotVerifiedException extends ForbiddenException {
    public OAuthEmailNotVerifiedException() {
        super("OAUTH_EMAIL_NOT_VERIFIED",
              "Tài khoản OAuth2 chưa xác thực email. Vui lòng xác thực email trước khi đăng nhập.");
    }
}
```

Response: HTTP 403 với code `OAUTH_EMAIL_NOT_VERIFIED` — rõ ràng, actionable cho client.

---

## 5. Files Changed

| File | Change |
|------|--------|
| `common/.../config/SupportProperties.java` | New — bind `app.support.*` env vars |
| `common/.../web/GlobalExceptionHandler.java` | Inject `SupportProperties`, update 5xx message |
| `common/.../exception/TooManyRequestsException.java` | New — 429 exception subclass |
| `auth/.../support/OAuthEmailNotVerifiedException.java` | Fix — extend `ForbiddenException` instead of `RuntimeException` |
| `app/src/main/resources/application.yml` | Add `app.support.email` and `app.support.phone` config |

---

## 6. What Does NOT Change

- Error response JSON shape (`success`, `code`, `message`, `error`, `timestamp`) — unchanged
- 4xx exception hierarchy (`BadRequestException`, `UnauthorizedException`, etc.) — unchanged
- Validation error handling (`VALIDATION_ERROR`) — unchanged
- Server-side logging of stack traces — unchanged

---

## 7. Environment Variables Required

| Variable | Example | Purpose |
|----------|---------|---------|
| `SUPPORT_EMAIL` | `support@fuoverflow.com` | Support contact email in 5xx message |
| `SUPPORT_PHONE` | `0900-000-000` | Support contact phone in 5xx message |

Should be set in all deployment environments. If not set, defaults to empty string — message sẽ hiển thị thiếu contact info nhưng app vẫn start được.

---

## 8. Out of Scope

- Localization of other error messages (4xx messages stay in English)
- Per-endpoint custom error messages
- Error code documentation / API error catalog
