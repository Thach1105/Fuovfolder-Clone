# Change Password Feature Design

**Date:** 2026-06-29  
**Status:** Approved

## Overview

Tính năng cho phép người dùng tự đổi/đặt mật khẩu cho tài khoản của mình:
- **Password user** (đăng ký email/password): nhập mật khẩu cũ → đổi trực tiếp.
- **OAuth user** (Google): xác nhận qua email → đặt mật khẩu lần đầu.

Sau khi đổi mật khẩu thành công, toàn bộ session khác bị revoke, chỉ giữ session hiện tại.

---

## Backend Design

### 1. Migration

`V34__password_reset_token_add_purpose.sql`

Thêm column `purpose VARCHAR(32) NOT NULL DEFAULT 'RESET_PASSWORD'` vào bảng `password_reset_tokens`.

Giá trị hợp lệ: `RESET_PASSWORD`, `SET_PASSWORD`.

### 2. Entity & Repository

Cập nhật `PasswordResetTokenEntity`:
- Thêm field `purpose` (`VARCHAR(32)`, mặc định `RESET_PASSWORD`).
- Factory method `createSetPassword(...)` tạo token với `purpose = SET_PASSWORD`.
- Getter `getPurpose()`.

Cập nhật `PasswordResetTokenRepository`:
- Thêm `findByTokenHashAndPurpose(String tokenHash, String purpose)` để tránh nhầm lẫn token giữa hai flow.
- Thêm `consumeActiveByUserIdAndPurpose(UUID userId, String purpose, Instant now)` — consume token cũ chỉ theo đúng purpose, tránh vô tình revoke token reset-password khi request set-password.

### 3. API Endpoints

#### Luồng A — Đổi mật khẩu (password user)

**`PATCH /api/v1/users/me/password`** — authenticated

Request DTO `ChangePasswordRequest`:
```json
{ "currentPassword": "...", "newPassword": "..." }
```

Validation:
- `currentPassword`: not blank
- `newPassword`: not blank, 8–128 ký tự

Response: `204 No Content`

Logic (`ChangePasswordService`):
1. Load `AuthUserView` qua `UserLookupService.findAuthUserById(userId)`.
2. Nếu `passwordHash == null` → 400 `NO_PASSWORD_TO_CHANGE`.
3. Validate `currentPassword` khớp hash qua `PasswordService.matches()` → sai → 400 `WRONG_PASSWORD`.
4. Validate policy `newPassword` qua `PasswordService.validatePolicy()`.
5. Encode + `UserPasswordService.updatePassword(userId, hash, now)`.
6. `UserSessionRepository.revokeAllExcept(userId, currentSid, "CHANGE_PASSWORD", now)`.

Endpoint: `AuthController` (hoặc tạo `UserPasswordController` trong auth module).  
**Lưu ý:** Nằm ở path `/api/v1/users/me/password` nhưng do logic dùng auth services nên đặt trong auth module, mount qua `@RequestMapping`.

#### Luồng B — Yêu cầu đặt mật khẩu lần đầu (OAuth user)

**`POST /api/v1/auth/password/set-request`** — authenticated

Request: no body  
Response: `200 { "message": "..." }`

Logic (`SetPasswordRequestService`):
1. Load user từ JWT `sub`.
2. Nếu `passwordHash != null` → 400 `PASSWORD_ALREADY_SET`.
3. Rate limit bằng `ResendRateLimiter` (key: `"set_password:{userId}"`).
4. Consume token cũ cùng purpose bằng `consumeActiveByUserIdAndPurpose(userId, "SET_PASSWORD", now)`.
5. Tạo token mới, lưu với `purpose = SET_PASSWORD`, TTL 1 giờ.
6. Gửi email link: `{frontendBaseUrl}/settings/security/set-password?token={rawToken}`.

#### Luồng C — Xác nhận đặt mật khẩu (OAuth user)

**`POST /api/v1/auth/password/set`** — public

Request DTO `SetPasswordRequest`:
```json
{ "token": "...", "password": "..." }
```

Response: `204 No Content`

Logic:
1. Lookup `findByTokenHashAndPurpose(hash, "SET_PASSWORD")`.
2. Token không tồn tại → 401 `TOKEN_INVALID`.
3. Token expired/consumed → 401 `TOKEN_EXPIRED`.
4. Load user, kiểm tra `passwordHash == null` → nếu đã có password → 400 `PASSWORD_ALREADY_SET`.
5. Encode + `UserPasswordService.updatePassword(userId, hash, now)`.
6. `consume(now)`, `consumeActiveByUserIdAndPurpose(userId, "SET_PASSWORD", now)`.
7. `UserSessionRepository.revokeAllByUserId(userId, "SET_PASSWORD", now)` (revoke tất cả, không có current session).

### 4. `UserProfileResponse` — thêm `hasPassword`

Thêm field `boolean hasPassword` vào `UserProfileResponse` và `UserMapper`.  
`hasPassword = passwordHash != null` — không trả hash, chỉ trả boolean.

### 5. Security

- Endpoint `PATCH /api/v1/users/me/password` yêu cầu authentication (JWT) — đã bảo vệ bởi `anyRequest().authenticated()`.
- Endpoint `POST /api/v1/auth/password/set-request` yêu cầu authentication (JWT) — đã bảo vệ bởi `anyRequest().authenticated()`.
- Endpoint `POST /api/v1/auth/password/set` là public (permit all), bảo vệ bằng token opaque — thêm vào `SecurityConfig.permitAll()`.
- Email link từ `set-request` dùng `frontendBaseUrl` từ `OAuth2Properties` — không dùng backend URL.

### 6. `UserSessionRepository` — thêm method mới

```java
@Modifying
@Query("update UserSessionEntity s set s.revokedAt = :now, s.revokeReason = :reason where s.userId = :userId and s.id != :excludeSid and s.revokedAt is null")
int revokeAllExcept(UUID userId, UUID excludeSid, String reason, Instant now);
```

---

## Frontend Design

### 1. Route mới: `/settings/security`

File: `Fuexam/app/(app)/settings/security/page.tsx`

Render có điều kiện dựa vào `user.hasPassword`:
- `hasPassword === true` → hiện `ChangePasswordForm`
- `hasPassword === false` → hiện `SetPasswordRequestCard`

### 2. Route mới: `/settings/security/set-password`

File: `Fuexam/app/(app)/settings/security/set-password/page.tsx`

- Đọc `?token=` từ URL search params.
- Nếu không có token → redirect về `/settings/security`.
- Render `SetPasswordForm` với token.

### 3. Components mới

**`Fuexam/components/auth/ChangePasswordForm.tsx`** — "use client"
- Form: mật khẩu hiện tại, mật khẩu mới, xác nhận mật khẩu.
- Validate client-side: mật khẩu mới ≥ 8 ký tự, hai trường confirm khớp nhau.
- Call `authApi.changePassword(currentPassword, newPassword)`.
- Success: hiện banner "Đổi mật khẩu thành công".
- Error: map `WRONG_PASSWORD` → "Mật khẩu hiện tại không đúng".
- Dùng `PasswordInput` component hiện có.

**`Fuexam/components/auth/SetPasswordRequestCard.tsx`** — "use client"
- Hiện text giải thích (tài khoản Google, có thể đặt mật khẩu để đăng nhập trực tiếp).
- Button "Gửi email xác nhận".
- Call `authApi.requestSetPassword()`.
- Success: thay button bằng banner "Kiểm tra hộp thư của bạn".

**`Fuexam/components/auth/SetPasswordForm.tsx`** — "use client"
- Nhận prop `token: string`.
- Form: mật khẩu mới, xác nhận mật khẩu.
- Call `authApi.setPassword(token, password)`.
- Success: redirect `/settings/security` với query `?passwordSet=1`.
- Token invalid/expired: hiện lỗi + link "Gửi lại email xác nhận" → `/settings/security`.

### 4. API client — `lib/api/auth.ts`

3 function mới:
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

### 5. Type `UserProfileResponse` — thêm `hasPassword`

`Fuexam/types/api.ts`:
```ts
export interface UserProfileResponse {
  // ... fields hiện có
  hasPassword: boolean;
}
```

### 6. Navigation settings

Kiểm tra xem settings có layout/sidebar riêng không. Nếu không có, không cần thay đổi — user điều hướng trực tiếp qua URL hoặc link từ trang profile. Nếu có, thêm link "Bảo mật" cạnh "Hồ sơ" và "Thông báo".

---

## Files Changed Summary

### Backend
| File | Action |
|------|--------|
| `app/src/main/resources/db/migration/V34__password_reset_token_add_purpose.sql` | Create |
| `auth/.../persistence/PasswordResetTokenEntity.java` | Update — thêm `purpose` field |
| `auth/.../persistence/PasswordResetTokenRepository.java` | Update — thêm query theo purpose |
| `auth/.../persistence/UserSessionRepository.java` | Update — thêm `revokeAllExcept` |
| `auth/.../application/ChangePasswordService.java` | Create |
| `auth/.../application/SetPasswordRequestService.java` | Create |
| `auth/.../api/dto/ChangePasswordRequest.java` | Create |
| `auth/.../api/dto/SetPasswordRequest.java` | Create |
| `auth/.../api/AuthController.java` | Update — thêm 3 endpoints |
| `auth/.../config/SecurityConfig.java` | Update — permit `/api/v1/auth/password/set` |
| `user/.../api/dto/UserProfileResponse.java` | Update — thêm `hasPassword` |
| `user/.../persistence/UserMapper.java` | Update — map `hasPassword` |

### Frontend (Fuexam)
| File | Action |
|------|--------|
| `types/api.ts` | Update — thêm `hasPassword` |
| `lib/api/auth.ts` | Update — thêm 3 functions |
| `app/(app)/settings/security/page.tsx` | Create |
| `app/(app)/settings/security/set-password/page.tsx` | Create |
| `components/auth/ChangePasswordForm.tsx` | Create |
| `components/auth/SetPasswordRequestCard.tsx` | Create |
| `components/auth/SetPasswordForm.tsx` | Create |

---

## Testing

- Unit test `ChangePasswordService`: success, wrong password, no password to change.
- Unit test `SetPasswordRequestService`: success, password already set.
- Unit test token validation trong `setPassword` flow: invalid token, expired token, already has password.
- FE: test render có điều kiện dựa vào `hasPassword`.
