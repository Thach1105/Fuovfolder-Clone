# Device Limit Fix & Session Management Design

**Date:** 2026-07-05
**Status:** Draft
**Scope:** Fix device-limit vulnerabilities (HIGH→LOW) + admin per-user device limit bypass + session management UI

---

## Problem Statement

The current device-limit enforcement has several gaps:

1. **HIGH — OAuth login bypasses `enforceDeviceLimit()`**: `OAuthSessionIssuer.issue()` creates sessions without checking the device limit, allowing unlimited concurrent sessions via Google login.
2. **MEDIUM — No tests for `enforceDeviceLimit()`**: Zero test coverage for the core session-limiting logic.
3. **LOW — No session management API/UI**: Users cannot view or manage their active sessions/devices.
4. **LOW — No "logout all devices" endpoint**: The repository method exists but is not exposed to users.
5. **LOW — No device identification**: Only raw User-Agent stored; no parsed label for UX.

Additionally, a new feature is needed: **admin can set a custom device limit per user** (including unlimited).

## Decisions

- **Approach:** Add `max_devices` column to `users` table (not a separate settings table)
- **Over-limit behavior:** Keep current behavior — silently revoke oldest session (no error to client)
- **Device identification:** Parse User-Agent into human-readable label (no browser fingerprinting)
- **UI scope:** Both Fuexam (user settings) and Fuexam-admin (admin panel)

---

## 1. Database Changes

### Migration: `V39__add_user_max_devices.sql`

```sql
ALTER TABLE users ADD COLUMN max_devices smallint DEFAULT NULL;
COMMENT ON COLUMN users.max_devices IS 'Per-user device limit. NULL=global default, 0=unlimited, >0=custom limit';
```

Semantics:
- `NULL` → use global config `auth.max-devices` (default: 2)
- `0` → unlimited (no enforcement)
- `> 0` → custom limit for this user

---

## 2. Backend Changes

### 2.1 Fix OAuth bypass (HIGH)

Extract device-limit enforcement into a shared component so both `AuthService` and `OAuthSessionIssuer` can use it.

**Option chosen:** Create a `DeviceLimitEnforcer` helper bean (or move `enforceDeviceLimit` to a package-private shared method). `OAuthSessionIssuer` will inject and call it before saving the session.

The `enforceDeviceLimit()` method will be updated to:

```java
void enforceDeviceLimit(UUID userId, Instant now) {
    Integer userMaxDevices = userLookupService.getMaxDevices(userId);
    int maxDevices = (userMaxDevices != null) ? userMaxDevices : authProperties.maxDevices();
    if (maxDevices <= 0) return; // 0 = unlimited, negative = disabled
    List<UUID> activeFamilies = sessions.findActiveFamilyIdsOrderedByAge(userId, now);
    int toRevoke = activeFamilies.size() - (maxDevices - 1);
    for (int i = 0; i < toRevoke; i++) {
        sessions.revokeFamily(activeFamilies.get(i), "DEVICE_LIMIT_EXCEEDED", now);
    }
}
```

Key change: query `users.max_devices` first, fall back to global config.

### 2.2 Session Management API — User endpoints

All under `/api/v1/auth/sessions`, require authentication.

#### `GET /api/v1/auth/sessions`

List active sessions for the current user.

**Response:**
```json
{
  "sessions": [
    {
      "id": "uuid",
      "ipAddress": "192.168.1.1",
      "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36...",
      "deviceLabel": "Chrome 126 on Windows 11",
      "issuedAt": "2026-07-01T10:00:00Z",
      "lastUsedAt": "2026-07-05T08:30:00Z",
      "current": true
    }
  ],
  "maxDevices": 2,
  "deviceLimitSource": "GLOBAL"
}
```

- `deviceLabel`: parsed from User-Agent (browser name + version + OS)
- `current`: true if this session's family matches the caller's refresh token family
- `deviceLimitSource`: `"GLOBAL"` | `"CUSTOM"` | `"UNLIMITED"`
- Returns one entry per active refresh-token family (not per rotated token)

#### `DELETE /api/v1/auth/sessions/{familyId}`

Revoke a specific session family. Cannot revoke the caller's own current session (returns 400).

**Response:** 204 No Content

#### `DELETE /api/v1/auth/sessions`

Revoke all sessions except the current one ("logout other devices").

**Response:** 204 No Content

### 2.3 Session Management API — Admin endpoints

#### `GET /api/v1/admin/users/{userId}/sessions`

Permission: `admin.user:read`

Same response shape as user endpoint but for any user. No `current` field.

#### `DELETE /api/v1/admin/users/{userId}/sessions/{familyId}`

Permission: `admin.user:update`

Force-revoke a specific session family.

#### `DELETE /api/v1/admin/users/{userId}/sessions`

Permission: `admin.user:update`

Force-revoke all sessions for a user.

#### `PUT /api/v1/admin/users/{userId}/device-limit`

Permission: `admin.user:update`

**Request:**
```json
{ "maxDevices": 5 }
```

- `maxDevices: null` → reset to global default
- `maxDevices: 0` → unlimited
- `maxDevices: 1-100` → custom limit

**Response:** 200 with updated value

### 2.4 User-Agent Parsing

Create a `UserAgentParser` utility class in `backend/common` that extracts browser name, version, and OS from a User-Agent string. Use simple regex patterns (no external library needed for MVP):

- Chrome, Firefox, Safari, Edge, Opera → browser name + major version
- Windows, macOS, Linux, Android, iOS → OS name
- Fallback: "Unknown browser" / "Unknown OS"

### 2.5 Repository Changes

Add to `UserSessionRepository`:

```java
// List one active session per family for a user (most recent session in each family)
@Query("""
    SELECT s FROM UserSessionEntity s
    WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.refreshExpiresAt > :now
    AND s.replacedBySessionId IS NULL
    ORDER BY s.lastUsedAt DESC NULLS LAST, s.issuedAt DESC
    """)
List<UserSessionEntity> findActiveSessionsForUser(UUID userId, Instant now);
```

Add to user lookup:

```java
// In UserLookupService or a new query
Integer getMaxDevices(UUID userId);
```

---

## 3. Frontend Changes — Fuexam (User)

### 3.1 New Settings Tab: "Thiết bị" (`/settings/devices`)

Add to `SettingsNav.tsx`: new nav item "Thiết bị" with monitor/device icon.

**Page layout:**
- Header: "Thiết bị đang đăng nhập" + badge showing count (e.g., "2/2 thiết bị")
- Info text showing current limit and source
- List of device cards:
  - Icon (desktop/mobile/tablet based on parsed OS)
  - Device label (e.g., "Chrome 126 on Windows 11")
  - IP address
  - "Đang dùng" badge for current session
  - "Đăng nhập lúc" + "Hoạt động lần cuối" timestamps
  - "Đăng xuất" button (disabled for current session)
- Footer: "Đăng xuất tất cả thiết bị khác" button (destructive style)

### 3.2 API Client

Add to `lib/api/auth.ts`:

```typescript
export async function getActiveSessions(): Promise<SessionListResponse>
export async function revokeSession(familyId: string): Promise<void>
export async function revokeOtherSessions(): Promise<void>
```

---

## 4. Frontend Changes — Fuexam-admin (Admin)

### 4.1 User Detail Page Enhancement

In `/users/[userId]/permissions/page.tsx`, add two new sections:

#### Section: "Giới hạn thiết bị"

- Dropdown select:
  - "Mặc định hệ thống (2)" → sets `maxDevices: null`
  - "Không giới hạn" → sets `maxDevices: 0`
  - "Tùy chỉnh" → shows number input (1-100)
- Save button

#### Section: "Phiên đăng nhập"

- Table with columns: Device, IP, Đăng nhập lúc, Hoạt động lần cuối, Actions
- "Đăng xuất" button per row
- "Đăng xuất tất cả" button in header

### 4.2 API Client

Add to `lib/api/admin.ts`:

```typescript
export async function getUserSessions(userId: string): Promise<SessionListResponse>
export async function revokeUserSession(userId: string, familyId: string): Promise<void>
export async function revokeAllUserSessions(userId: string): Promise<void>
export async function setUserDeviceLimit(userId: string, maxDevices: number | null): Promise<void>
```

---

## 5. Tests

### 5.1 Unit Tests

- `DeviceLimitEnforcerTest`:
  - `max_devices = NULL` → uses global config
  - `max_devices = 0` → skips enforcement
  - `max_devices = 5` → enforces custom value
  - 0 active families → no revocation
  - Exactly at limit → no revocation
  - Over limit → revokes oldest families

### 5.2 Integration Tests

- `OAuthSessionIssuerTest`: verify device limit is enforced during OAuth session creation
- `AuthService` login flow: verify device limit with per-user override

### 5.3 MVC Tests

- `GET /api/v1/auth/sessions`: returns only caller's sessions, not other users'
- `DELETE /api/v1/auth/sessions/{familyId}`: cannot revoke own current session (400), can revoke others (204)
- `DELETE /api/v1/auth/sessions`: revokes all except current
- Admin endpoints: verify permission checks

---

## 6. Implementation Priority

| Priority | Task | Effort |
|---|---|---|
| 1 (HIGH) | Fix OAuth bypass — extract `DeviceLimitEnforcer`, wire into `OAuthSessionIssuer` | S |
| 2 (HIGH) | Migration `V39` — add `max_devices` column | XS |
| 3 (HIGH) | Update `enforceDeviceLimit()` to read per-user `max_devices` | S |
| 4 (MEDIUM) | Tests for device limit logic | M |
| 5 (LOW) | Session listing API + repository query | M |
| 6 (LOW) | User-Agent parser utility | S |
| 7 (LOW) | Admin endpoints (sessions + device-limit CRUD) | M |
| 8 (LOW) | Fuexam: `/settings/devices` page | M |
| 9 (LOW) | Fuexam-admin: device-limit + sessions sections | M |
| 10 (LOW) | MVC tests for session endpoints | M |
