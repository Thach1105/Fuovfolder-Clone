# Device Limit Fix & Session Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all device-limit vulnerabilities (OAuth bypass, missing tests), add per-user custom device limit (admin-configurable), and build session management UI for both users and admins.

**Architecture:** Extract `DeviceLimitEnforcer` as a shared bean used by both `AuthService` and `OAuthSessionIssuer`. Add `max_devices` column to `users` table for per-user overrides (NULL=global, 0=unlimited, >0=custom). New session listing/revocation endpoints served by a `SessionManagementService`. FE additions in both Fuexam (user settings) and Fuexam-admin (admin panel).

**Tech Stack:** Java 21, Spring Boot 3.5, JPA/Hibernate, Flyway, JUnit 5/Mockito, Next.js 15, Radix UI/shadcn, TypeScript

## Global Constraints

- PostgreSQL is the only DB. No FK constraints.
- Hibernate `ddl-auto` = `validate`. Schema changes via Flyway only.
- Constructor injection. Records for DTOs.
- `ApiResponse<T>` envelope for all responses.
- `@RequirePermission` for RBAC. Permissions: `admin.user:read`, `admin.user:update`.
- Soft delete via `deleted_at`.
- Auth cookies: HttpOnly, Secure, SameSite=Lax.
- FE API calls via `apiFetch()` with auto-refresh on 401.

---

### Task 1: Flyway migration — add `max_devices` column to users

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V39__add_user_max_devices.sql`
- Modify: `backend/user/src/main/java/com/fuoverflow/user/persistence/UserEntity.java`

**Interfaces:**
- Produces: `users.max_devices` column (smallint, nullable, default NULL). `UserEntity.getMaxDevices()` / `UserEntity.setMaxDevices(Short)`.

- [ ] **Step 1: Create Flyway migration**

```sql
-- V39__add_user_max_devices.sql
ALTER TABLE users ADD COLUMN max_devices smallint DEFAULT NULL;
COMMENT ON COLUMN users.max_devices IS 'Per-user device limit. NULL=global default, 0=unlimited, >0=custom limit';
```

- [ ] **Step 2: Add field to UserEntity**

In `backend/user/src/main/java/com/fuoverflow/user/persistence/UserEntity.java`, add after the `lockVersion` field:

```java
@Column(name = "max_devices")
private Short maxDevices;
```

Add getter and setter:

```java
public Short getMaxDevices() { return maxDevices; }

public void setMaxDevices(Short maxDevices) { this.maxDevices = maxDevices; }
```

- [ ] **Step 3: Add query to UserRepository**

In `backend/user/src/main/java/com/fuoverflow/user/persistence/UserRepository.java`, add:

```java
@Query("SELECT u.maxDevices FROM UserEntity u WHERE u.id = :userId AND u.deletedAt IS NULL")
Optional<Short> findMaxDevicesById(@Param("userId") UUID userId);
```

- [ ] **Step 4: Add method to UserLookupService**

In `backend/user/src/main/java/com/fuoverflow/user/application/UserLookupService.java`, add:

```java
@Transactional(readOnly = true)
public Short getMaxDevices(UUID userId) {
    return repository.findMaxDevicesById(userId).orElse(null);
}
```

- [ ] **Step 5: Verify migration runs**

Run:
```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS (Hibernate validates schema matches entities)

- [ ] **Step 6: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V39__add_user_max_devices.sql \
      backend/user/src/main/java/com/fuoverflow/user/persistence/UserEntity.java \
      backend/user/src/main/java/com/fuoverflow/user/persistence/UserRepository.java \
      backend/user/src/main/java/com/fuoverflow/user/application/UserLookupService.java
git commit -m "feat: add per-user max_devices column for custom device limits"
```

---

### Task 2: Extract DeviceLimitEnforcer + fix OAuth bypass

**Files:**
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/application/DeviceLimitEnforcer.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/application/AuthService.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthSessionIssuer.java`
- Test: `backend/auth/src/test/java/com/fuoverflow/auth/application/DeviceLimitEnforcerTest.java`

**Interfaces:**
- Consumes: `UserLookupService.getMaxDevices(UUID)` from Task 1, `AuthProperties.maxDevices()`, `UserSessionRepository.findActiveFamilyIdsOrderedByAge()`, `UserSessionRepository.revokeFamily()`
- Produces: `DeviceLimitEnforcer.enforce(UUID userId, Instant now)` — called before every new session creation

- [ ] **Step 1: Write the failing tests**

Create `backend/auth/src/test/java/com/fuoverflow/auth/application/DeviceLimitEnforcerTest.java`:

```java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.user.application.UserLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceLimitEnforcerTest {
    @Mock private UserSessionRepository sessions;
    @Mock private UserLookupService users;
    @Mock private AuthProperties authProperties;

    private DeviceLimitEnforcer enforcer;

    @BeforeEach
    void setUp() {
        enforcer = new DeviceLimitEnforcer(sessions, users, authProperties);
    }

    @Test
    void enforce_userMaxDevicesNull_usesGlobalConfig() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID family1 = UUID.randomUUID();
        UUID family2 = UUID.randomUUID();
        UUID family3 = UUID.randomUUID();

        when(users.getMaxDevices(userId)).thenReturn(null);
        when(authProperties.maxDevices()).thenReturn(2);
        when(sessions.findActiveFamilyIdsOrderedByAge(userId, now))
                .thenReturn(List.of(family1, family2, family3));

        enforcer.enforce(userId, now);

        verify(sessions).revokeFamily(family1, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions).revokeFamily(family2, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions, never()).revokeFamily(eq(family3), any(), any());
    }

    @Test
    void enforce_userMaxDevicesZero_skipsEnforcement() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        when(users.getMaxDevices(userId)).thenReturn((short) 0);

        enforcer.enforce(userId, now);

        verify(sessions, never()).findActiveFamilyIdsOrderedByAge(any(), any());
        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void enforce_userMaxDevicesCustom_usesCustomValue() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID family1 = UUID.randomUUID();
        UUID family2 = UUID.randomUUID();
        UUID family3 = UUID.randomUUID();

        when(users.getMaxDevices(userId)).thenReturn((short) 5);
        when(sessions.findActiveFamilyIdsOrderedByAge(userId, now))
                .thenReturn(List.of(family1, family2, family3));

        enforcer.enforce(userId, now);

        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void enforce_noActiveFamilies_doesNothing() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        when(users.getMaxDevices(userId)).thenReturn(null);
        when(authProperties.maxDevices()).thenReturn(2);
        when(sessions.findActiveFamilyIdsOrderedByAge(userId, now))
                .thenReturn(List.of());

        enforcer.enforce(userId, now);

        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void enforce_exactlyAtLimit_doesNotRevoke() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID family1 = UUID.randomUUID();

        when(users.getMaxDevices(userId)).thenReturn(null);
        when(authProperties.maxDevices()).thenReturn(2);
        when(sessions.findActiveFamilyIdsOrderedByAge(userId, now))
                .thenReturn(List.of(family1));

        enforcer.enforce(userId, now);

        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void enforce_globalConfigDisabled_skipsEnforcement() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        when(users.getMaxDevices(userId)).thenReturn(null);
        when(authProperties.maxDevices()).thenReturn(0);

        enforcer.enforce(userId, now);

        verify(sessions, never()).findActiveFamilyIdsOrderedByAge(any(), any());
        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void enforce_overLimitByMany_revokesOldestFirst() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        UUID f3 = UUID.randomUUID();
        UUID f4 = UUID.randomUUID();
        UUID f5 = UUID.randomUUID();

        when(users.getMaxDevices(userId)).thenReturn((short) 2);
        when(sessions.findActiveFamilyIdsOrderedByAge(userId, now))
                .thenReturn(List.of(f1, f2, f3, f4, f5));

        enforcer.enforce(userId, now);

        verify(sessions).revokeFamily(f1, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions).revokeFamily(f2, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions).revokeFamily(f3, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions).revokeFamily(f4, "DEVICE_LIMIT_EXCEEDED", now);
        verify(sessions, never()).revokeFamily(eq(f5), any(), any());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd backend && mvn -q test -pl auth -Dtest=DeviceLimitEnforcerTest
```

Expected: FAIL — `DeviceLimitEnforcer` class does not exist

- [ ] **Step 3: Create DeviceLimitEnforcer**

Create `backend/auth/src/main/java/com/fuoverflow/auth/application/DeviceLimitEnforcer.java`:

```java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.user.application.UserLookupService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class DeviceLimitEnforcer {
    private final UserSessionRepository sessions;
    private final UserLookupService users;
    private final AuthProperties authProperties;

    public DeviceLimitEnforcer(UserSessionRepository sessions, UserLookupService users,
                               AuthProperties authProperties) {
        this.sessions = sessions;
        this.users = users;
        this.authProperties = authProperties;
    }

    public void enforce(UUID userId, Instant now) {
        Short userMaxDevices = users.getMaxDevices(userId);
        int maxDevices = (userMaxDevices != null) ? userMaxDevices : authProperties.maxDevices();
        if (maxDevices <= 0) {
            return;
        }
        List<UUID> activeFamilies = sessions.findActiveFamilyIdsOrderedByAge(userId, now);
        int toRevoke = activeFamilies.size() - (maxDevices - 1);
        for (int i = 0; i < toRevoke; i++) {
            sessions.revokeFamily(activeFamilies.get(i), "DEVICE_LIMIT_EXCEEDED", now);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd backend && mvn -q test -pl auth -Dtest=DeviceLimitEnforcerTest
```

Expected: All 7 tests PASS

- [ ] **Step 5: Wire DeviceLimitEnforcer into AuthService**

In `backend/auth/src/main/java/com/fuoverflow/auth/application/AuthService.java`:

1. Add field and constructor parameter:

```java
private final DeviceLimitEnforcer deviceLimitEnforcer;
```

Update constructor to accept `DeviceLimitEnforcer deviceLimitEnforcer` and assign `this.deviceLimitEnforcer = deviceLimitEnforcer;`.

2. Replace the private `enforceDeviceLimit` method body in `createSession()`:

Change line 140 from:
```java
enforceDeviceLimit(user.id(), now);
```
to:
```java
deviceLimitEnforcer.enforce(user.id(), now);
```

3. Remove the private `enforceDeviceLimit()` method entirely (lines 149-161).

- [ ] **Step 6: Wire DeviceLimitEnforcer into OAuthSessionIssuer**

In `backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthSessionIssuer.java`:

1. Add field and constructor parameter:

```java
private final DeviceLimitEnforcer deviceLimitEnforcer;
```

Update constructor:
```java
public OAuthSessionIssuer(UserLookupService users, JwtService jwt,
                          UserSessionRepository sessions, CookieService cookies,
                          DeviceLimitEnforcer deviceLimitEnforcer) {
    this.users = users;
    this.jwt = jwt;
    this.sessions = sessions;
    this.cookies = cookies;
    this.deviceLimitEnforcer = deviceLimitEnforcer;
}
```

2. In the `issue()` method, add `deviceLimitEnforcer.enforce(userId, now);` right after `Instant now = Instant.now();` (before `UUID sessionId = ...`):

```java
@Transactional
public AuthService.AuthTokenBundle issue(UUID userId, ClientContext context) {
    AuthUserView user = users.findAuthUserById(userId)
            .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "User not found"));

    if (!user.status().canAuthenticate()) {
        throw new ForbiddenException("USER_DISABLED", "User cannot authenticate");
    }

    Instant now = Instant.now();
    deviceLimitEnforcer.enforce(userId, now);
    UUID sessionId = UUID.randomUUID();
    // ... rest unchanged
```

- [ ] **Step 7: Update OAuthSessionIssuerTest**

In `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthSessionIssuerTest.java`:

Add mock field:
```java
@Mock private DeviceLimitEnforcer deviceLimitEnforcer;
```

Update `setUp()`:
```java
@BeforeEach
void setUp() {
    issuer = new OAuthSessionIssuer(users, jwt, sessions, cookies, deviceLimitEnforcer);
}
```

Add new test to verify device limit is called:
```java
@Test
void issue_activeUser_enforcesDeviceLimit() {
    UUID userId = UUID.randomUUID();
    Instant now = Instant.now();
    AuthUserView user = new AuthUserView(
            userId, "user@example.com", "username", "hash", "Display Name",
            UserStatus.ACTIVE, List.of("USER"), 1L, List.of(), false, true, now, now, null
    );
    when(users.findAuthUserById(userId)).thenReturn(Optional.of(user));

    TokenPair pair = new TokenPair("access", "refresh", "refreshHash",
            UUID.randomUUID(), UUID.randomUUID(), now,
            now.plusSeconds(600), now.plusSeconds(2592000));
    when(jwt.generate(any(), any(), any())).thenReturn(pair);

    ClientContext context = new ClientContext("127.0.0.1", "Test Agent");
    issuer.issue(userId, context);

    verify(deviceLimitEnforcer).enforce(eq(userId), any(Instant.class));
}
```

- [ ] **Step 8: Run all auth tests**

Run:
```bash
cd backend && mvn -q test -pl auth
```

Expected: All tests PASS

- [ ] **Step 9: Compile full project**

Run:
```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/application/DeviceLimitEnforcer.java \
      backend/auth/src/main/java/com/fuoverflow/auth/application/AuthService.java \
      backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthSessionIssuer.java \
      backend/auth/src/test/java/com/fuoverflow/auth/application/DeviceLimitEnforcerTest.java \
      backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthSessionIssuerTest.java
git commit -m "fix: extract DeviceLimitEnforcer and wire into OAuth flow

OAuth login previously bypassed enforceDeviceLimit entirely.
Now both AuthService and OAuthSessionIssuer use the shared
DeviceLimitEnforcer which also supports per-user max_devices."
```

---

### Task 3: User-Agent parser utility

**Files:**
- Create: `backend/common/src/main/java/com/fuoverflow/common/support/UserAgentParser.java`
- Test: `backend/common/src/test/java/com/fuoverflow/common/support/UserAgentParserTest.java`

**Interfaces:**
- Produces: `UserAgentParser.parse(String userAgent)` → `DeviceLabel` record with `browser`, `os`, `label` fields

- [ ] **Step 1: Write the failing tests**

Create `backend/common/src/test/java/com/fuoverflow/common/support/UserAgentParserTest.java`:

```java
package com.fuoverflow.common.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 | Chrome 126 | Windows",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 | Chrome 125 | macOS",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0 | Firefox 128 | Windows",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15 | Safari 17 | macOS",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0 | Edge 126 | Windows",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 | Chrome 126 | Android",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1 | Safari 17 | iOS",
    })
    void parse_knownBrowsers(String ua, String expectedBrowser, String expectedOs) {
        UserAgentParser.DeviceLabel label = UserAgentParser.parse(ua);
        assertThat(label.browser()).isEqualTo(expectedBrowser);
        assertThat(label.os()).isEqualTo(expectedOs);
        assertThat(label.label()).isEqualTo(expectedBrowser + " on " + expectedOs);
    }

    @Test
    void parse_nullUserAgent_returnsUnknown() {
        UserAgentParser.DeviceLabel label = UserAgentParser.parse(null);
        assertThat(label.browser()).isEqualTo("Unknown browser");
        assertThat(label.os()).isEqualTo("Unknown OS");
    }

    @Test
    void parse_emptyUserAgent_returnsUnknown() {
        UserAgentParser.DeviceLabel label = UserAgentParser.parse("");
        assertThat(label.browser()).isEqualTo("Unknown browser");
        assertThat(label.os()).isEqualTo("Unknown OS");
    }

    @Test
    void parse_unknownUserAgent_returnsUnknown() {
        UserAgentParser.DeviceLabel label = UserAgentParser.parse("curl/7.88.1");
        assertThat(label.browser()).isEqualTo("Unknown browser");
        assertThat(label.os()).isEqualTo("Unknown OS");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd backend && mvn -q test -pl common -Dtest=UserAgentParserTest
```

Expected: FAIL — class does not exist

- [ ] **Step 3: Implement UserAgentParser**

Create `backend/common/src/main/java/com/fuoverflow/common/support/UserAgentParser.java`:

```java
package com.fuoverflow.common.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserAgentParser {

    private UserAgentParser() {}

    public record DeviceLabel(String browser, String os, String label) {}

    private static final Pattern EDGE = Pattern.compile("Edg/(\\d+)");
    private static final Pattern CHROME = Pattern.compile("Chrome/(\\d+)");
    private static final Pattern FIREFOX = Pattern.compile("Firefox/(\\d+)");
    private static final Pattern SAFARI_VERSION = Pattern.compile("Version/(\\d+)");
    private static final Pattern OPERA = Pattern.compile("OPR/(\\d+)");

    public static DeviceLabel parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new DeviceLabel("Unknown browser", "Unknown OS", "Unknown browser on Unknown OS");
        }
        String browser = parseBrowser(userAgent);
        String os = parseOs(userAgent);
        return new DeviceLabel(browser, os, browser + " on " + os);
    }

    private static String parseBrowser(String ua) {
        Matcher m;
        m = EDGE.matcher(ua);
        if (m.find()) return "Edge " + m.group(1);
        m = OPERA.matcher(ua);
        if (m.find()) return "Opera " + m.group(1);
        m = CHROME.matcher(ua);
        if (m.find()) {
            if (!ua.contains("Safari")) return "Chrome " + m.group(1);
            return "Chrome " + m.group(1);
        }
        m = FIREFOX.matcher(ua);
        if (m.find()) return "Firefox " + m.group(1);
        if (ua.contains("Safari")) {
            m = SAFARI_VERSION.matcher(ua);
            if (m.find()) return "Safari " + m.group(1);
        }
        return "Unknown browser";
    }

    private static String parseOs(String ua) {
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) return "iOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Linux")) return "Linux";
        if (ua.contains("CrOS")) return "Chrome OS";
        return "Unknown OS";
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:
```bash
cd backend && mvn -q test -pl common -Dtest=UserAgentParserTest
```

Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/common/src/main/java/com/fuoverflow/common/support/UserAgentParser.java \
      backend/common/src/test/java/com/fuoverflow/common/support/UserAgentParserTest.java
git commit -m "feat: add UserAgentParser utility for device label display"
```

---

### Task 4: Session management service + repository queries + DTOs

**Files:**
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/application/SessionManagementService.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SessionResponse.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SessionListResponse.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/persistence/UserSessionRepository.java`

**Interfaces:**
- Consumes: `UserSessionRepository`, `UserLookupService.getMaxDevices()`, `AuthProperties.maxDevices()`, `UserAgentParser.parse()`
- Produces:
  - `SessionManagementService.listSessions(UUID userId, UUID currentFamilyId)` → `SessionListResponse`
  - `SessionManagementService.revokeSession(UUID userId, UUID familyId, UUID currentFamilyId)` → void
  - `SessionManagementService.revokeOtherSessions(UUID userId, UUID currentSessionId)` → void

- [ ] **Step 1: Create DTOs**

Create `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SessionResponse.java`:

```java
package com.fuoverflow.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String ipAddress,
        String userAgent,
        String deviceLabel,
        Instant issuedAt,
        Instant lastUsedAt,
        boolean current
) {}
```

Create `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SessionListResponse.java`:

```java
package com.fuoverflow.auth.api.dto;

import java.util.List;

public record SessionListResponse(
        List<SessionResponse> sessions,
        int maxDevices,
        String deviceLimitSource
) {}
```

- [ ] **Step 2: Add repository query**

In `backend/auth/src/main/java/com/fuoverflow/auth/persistence/UserSessionRepository.java`, add:

```java
@Query("""
    SELECT s FROM UserSessionEntity s
    WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.refreshExpiresAt > :now
    AND s.replacedBySessionId IS NULL
    ORDER BY s.lastUsedAt DESC NULLS LAST, s.issuedAt DESC
    """)
List<UserSessionEntity> findActiveSessionsForUser(@Param("userId") UUID userId, @Param("now") Instant now);
```

Also add for revoking all except current family:

```java
@Modifying
@Query("UPDATE UserSessionEntity s SET s.revokedAt = :now, s.revokedReason = :reason WHERE s.userId = :userId AND s.refreshTokenFamilyId != :excludeFamilyId AND s.revokedAt IS NULL")
int revokeAllExceptFamily(@Param("userId") UUID userId, @Param("excludeFamilyId") UUID excludeFamilyId, @Param("reason") String reason, @Param("now") Instant now);
```

- [ ] **Step 3: Create SessionManagementService**

Create `backend/auth/src/main/java/com/fuoverflow/auth/application/SessionManagementService.java`:

```java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.api.dto.SessionListResponse;
import com.fuoverflow.auth.api.dto.SessionResponse;
import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.persistence.UserSessionEntity;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.support.UserAgentParser;
import com.fuoverflow.user.application.UserLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionManagementService {
    private final UserSessionRepository sessions;
    private final UserLookupService users;
    private final AuthProperties authProperties;

    public SessionManagementService(UserSessionRepository sessions, UserLookupService users,
                                     AuthProperties authProperties) {
        this.sessions = sessions;
        this.users = users;
        this.authProperties = authProperties;
    }

    @Transactional(readOnly = true)
    public SessionListResponse listSessions(UUID userId, UUID currentFamilyId) {
        Instant now = Instant.now();
        List<UserSessionEntity> active = sessions.findActiveSessionsForUser(userId, now);

        List<SessionResponse> sessionDtos = active.stream().map(s -> {
            UserAgentParser.DeviceLabel label = UserAgentParser.parse(s.getUserAgent());
            boolean current = currentFamilyId != null && s.getRefreshTokenFamilyId().equals(currentFamilyId);
            return new SessionResponse(
                    s.getRefreshTokenFamilyId(),
                    s.getIpAddress(),
                    s.getUserAgent(),
                    label.label(),
                    s.getIssuedAt(),
                    s.getLastUsedAt(),
                    current
            );
        }).toList();

        Short userMax = users.getMaxDevices(userId);
        int maxDevices;
        String source;
        if (userMax == null) {
            maxDevices = authProperties.maxDevices();
            source = "GLOBAL";
        } else if (userMax == 0) {
            maxDevices = 0;
            source = "UNLIMITED";
        } else {
            maxDevices = userMax;
            source = "CUSTOM";
        }

        return new SessionListResponse(sessionDtos, maxDevices, source);
    }

    @Transactional
    public void revokeSession(UUID userId, UUID familyId, UUID currentFamilyId) {
        if (currentFamilyId != null && familyId.equals(currentFamilyId)) {
            throw new BadRequestException("CANNOT_REVOKE_CURRENT", "Không thể đăng xuất phiên hiện tại");
        }
        int revoked = sessions.revokeFamily(familyId, "REMOTE_LOGOUT", Instant.now());
        if (revoked == 0) {
            throw new NotFoundException("SESSION_NOT_FOUND", "Không tìm thấy phiên đăng nhập");
        }
    }

    @Transactional
    public void revokeOtherSessions(UUID userId, UUID currentFamilyId) {
        sessions.revokeAllExceptFamily(userId, currentFamilyId, "REMOTE_LOGOUT_ALL", Instant.now());
    }

    @Transactional
    public void adminRevokeSession(UUID familyId) {
        int revoked = sessions.revokeFamily(familyId, "ADMIN_REVOKE", Instant.now());
        if (revoked == 0) {
            throw new NotFoundException("SESSION_NOT_FOUND", "Không tìm thấy phiên đăng nhập");
        }
    }

    @Transactional
    public void adminRevokeAllSessions(UUID userId) {
        sessions.revokeAllByUserId(userId, "ADMIN_REVOKE_ALL", Instant.now());
    }
}
```

- [ ] **Step 4: Compile**

Run:
```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SessionResponse.java \
      backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SessionListResponse.java \
      backend/auth/src/main/java/com/fuoverflow/auth/application/SessionManagementService.java \
      backend/auth/src/main/java/com/fuoverflow/auth/persistence/UserSessionRepository.java
git commit -m "feat: add SessionManagementService with list/revoke session APIs"
```

---

### Task 5: Session management controller endpoints (user + admin)

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/api/AuthController.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/api/AdminSessionController.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SetDeviceLimitRequest.java`

**Interfaces:**
- Consumes: `SessionManagementService` from Task 4, `UserSessionRepository.findByIdAndRevokedAtIsNull()` for resolving current session's familyId from JWT `sid` claim
- Produces:
  - `GET /api/v1/auth/sessions` (user)
  - `DELETE /api/v1/auth/sessions/{familyId}` (user)
  - `DELETE /api/v1/auth/sessions` (user)
  - `GET /api/v1/admin/users/{userId}/sessions` (admin)
  - `DELETE /api/v1/admin/users/{userId}/sessions/{familyId}` (admin)
  - `DELETE /api/v1/admin/users/{userId}/sessions` (admin)
  - `PUT /api/v1/admin/users/{userId}/device-limit` (admin)

- [ ] **Step 1: Create SetDeviceLimitRequest DTO**

Create `backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SetDeviceLimitRequest.java`:

```java
package com.fuoverflow.auth.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SetDeviceLimitRequest(
        @Min(0) @Max(100)
        Short maxDevices
) {}
```

- [ ] **Step 2: Add helper to resolve current family ID from JWT**

In `AuthController.java`, add a private helper method to extract the session ID from the JWT `sid` claim and look up the family ID:

```java
private UUID currentFamilyId(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwt) {
        String sid = jwt.getToken().getClaimAsString("sid");
        if (sid != null) {
            try {
                UUID sessionId = UUID.fromString(sid);
                return sessionRepository.findByIdAndRevokedAtIsNull(sessionId)
                        .map(UserSessionEntity::getRefreshTokenFamilyId)
                        .orElse(null);
            } catch (IllegalArgumentException ignored) {}
        }
    }
    return null;
}
```

Add `SessionManagementService` and `UserSessionRepository` as constructor dependencies to `AuthController`:

```java
private final SessionManagementService sessionManagementService;
private final UserSessionRepository sessionRepository;
```

(import `com.fuoverflow.auth.persistence.UserSessionEntity` and `com.fuoverflow.auth.persistence.UserSessionRepository`)

- [ ] **Step 3: Add user session endpoints to AuthController**

Add these methods to `AuthController`:

```java
@GetMapping("/sessions")
public ApiResponse<SessionListResponse> listSessions(Authentication authentication) {
    UUID userId = UUID.fromString(authentication.getName());
    UUID familyId = currentFamilyId(authentication);
    return ApiResponse.ok(sessionManagementService.listSessions(userId, familyId));
}

@DeleteMapping("/sessions/{familyId}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void revokeSession(@PathVariable UUID familyId, Authentication authentication) {
    UUID userId = UUID.fromString(authentication.getName());
    UUID currentFamily = currentFamilyId(authentication);
    sessionManagementService.revokeSession(userId, familyId, currentFamily);
}

@DeleteMapping("/sessions")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void revokeOtherSessions(Authentication authentication) {
    UUID userId = UUID.fromString(authentication.getName());
    UUID currentFamily = currentFamilyId(authentication);
    sessionManagementService.revokeOtherSessions(userId, currentFamily);
}
```

Add import for `SessionListResponse`:
```java
import com.fuoverflow.auth.api.dto.SessionListResponse;
```

- [ ] **Step 4: Create AdminSessionController**

Create `backend/auth/src/main/java/com/fuoverflow/auth/api/AdminSessionController.java`:

```java
package com.fuoverflow.auth.api;

import com.fuoverflow.auth.api.dto.SessionListResponse;
import com.fuoverflow.auth.api.dto.SetDeviceLimitRequest;
import com.fuoverflow.auth.application.SessionManagementService;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users/{userId}")
@RequirePermission("admin.panel:access")
public class AdminSessionController {
    private final SessionManagementService sessionManagement;
    private final UserRepository userRepository;

    public AdminSessionController(SessionManagementService sessionManagement,
                                   UserRepository userRepository) {
        this.sessionManagement = sessionManagement;
        this.userRepository = userRepository;
    }

    @GetMapping("/sessions")
    @RequirePermission("admin.user:read")
    public ApiResponse<SessionListResponse> listSessions(@PathVariable UUID userId) {
        return ApiResponse.ok(sessionManagement.listSessions(userId, null));
    }

    @DeleteMapping("/sessions/{familyId}")
    @RequirePermission("admin.user:update")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID userId, @PathVariable UUID familyId) {
        sessionManagement.adminRevokeSession(familyId);
    }

    @DeleteMapping("/sessions")
    @RequirePermission("admin.user:update")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(@PathVariable UUID userId) {
        sessionManagement.adminRevokeAllSessions(userId);
    }

    @PutMapping("/device-limit")
    @RequirePermission("admin.user:update")
    public ApiResponse<SetDeviceLimitRequest> setDeviceLimit(
            @PathVariable UUID userId,
            @Valid @RequestBody SetDeviceLimitRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Không tìm thấy người dùng"));
        user.setMaxDevices(request.maxDevices());
        userRepository.save(user);
        return ApiResponse.ok(new SetDeviceLimitRequest(user.getMaxDevices()));
    }
}
```

- [ ] **Step 5: Verify security config**

The security config at `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java` uses explicit `permitAll` paths (register, login, refresh, etc.) and `.anyRequest().authenticated()`. The new `/api/v1/auth/sessions` path is **NOT** in the permitAll list, so it correctly requires authentication. No security config changes needed.

The admin endpoints under `/api/v1/admin/users/{userId}` are also not in permitAll, plus `@RequirePermission("admin.panel:access")` on the controller class adds RBAC enforcement.

No action required — just verify by reading the security config file.

- [ ] **Step 6: Compile**

Run:
```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/api/AuthController.java \
      backend/auth/src/main/java/com/fuoverflow/auth/api/AdminSessionController.java \
      backend/auth/src/main/java/com/fuoverflow/auth/api/dto/SetDeviceLimitRequest.java
git commit -m "feat: add session management endpoints for users and admins

User endpoints: GET/DELETE /api/v1/auth/sessions
Admin endpoints: sessions CRUD + PUT device-limit under /api/v1/admin/users/{userId}"
```

---

### Task 6: Fuexam — API client + types for session management

**Files:**
- Modify: `Fuexam/types/api.ts`
- Modify: `Fuexam/lib/api/auth.ts`

**Interfaces:**
- Produces: `getActiveSessions()`, `revokeSession()`, `revokeOtherSessions()` API functions + TypeScript types

- [ ] **Step 1: Add TypeScript types**

In `Fuexam/types/api.ts`, add at the end:

```typescript
export interface SessionResponse {
  id: string;
  ipAddress: string | null;
  userAgent: string | null;
  deviceLabel: string;
  issuedAt: string;
  lastUsedAt: string | null;
  current: boolean;
}

export interface SessionListResponse {
  sessions: SessionResponse[];
  maxDevices: number;
  deviceLimitSource: "GLOBAL" | "CUSTOM" | "UNLIMITED";
}
```

- [ ] **Step 2: Add API functions**

In `Fuexam/lib/api/auth.ts`, add at the end:

```typescript
export function getActiveSessions() {
  return apiFetch<SessionListResponse>(`${API_V1}/auth/sessions`);
}

export function revokeSession(familyId: string) {
  return apiFetch<void>(`${API_V1}/auth/sessions/${familyId}`, {
    method: "DELETE",
  });
}

export function revokeOtherSessions() {
  return apiFetch<void>(`${API_V1}/auth/sessions`, { method: "DELETE" });
}
```

Add `SessionListResponse` to the import from types:

```typescript
import type {
  AuthTokenResponse,
  AuthenticatedUserResponse,
  CompletePendingProfileRequest,
  ForgotPasswordResponse,
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
  SessionListResponse,
  UserProfileResponse,
} from "@/types/api";
```

- [ ] **Step 3: Commit**

```bash
git add Fuexam/types/api.ts Fuexam/lib/api/auth.ts
git commit -m "feat(fuexam): add session management API client and types"
```

---

### Task 7: Fuexam — Settings devices page

**Files:**
- Create: `Fuexam/app/(app)/settings/devices/page.tsx`
- Modify: `Fuexam/components/settings/SettingsNav.tsx`

**Interfaces:**
- Consumes: `getActiveSessions()`, `revokeSession()`, `revokeOtherSessions()` from Task 6

- [ ] **Step 1: Add "Thiết bị" tab to SettingsNav**

In `Fuexam/components/settings/SettingsNav.tsx`, add to the `tabs` array:

```typescript
const tabs = [
  { href: "/settings/profile", label: "Hồ sơ" },
  { href: "/settings/security", label: "Bảo mật" },
  { href: "/settings/devices", label: "Thiết bị" },
  { href: "/settings/notifications", label: "Thông báo" },
];
```

- [ ] **Step 2: Create devices settings page**

Create `Fuexam/app/(app)/settings/devices/page.tsx`:

```tsx
"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Monitor, Smartphone, Tablet, LogOut } from "lucide-react";
import { SettingsNav } from "@/components/settings/SettingsNav";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { ApiError } from "@/lib/api/client";
import {
  getActiveSessions,
  revokeSession,
  revokeOtherSessions,
} from "@/lib/api/auth";
import type { SessionListResponse, SessionResponse } from "@/types/api";

function deviceIcon(userAgent: string | null) {
  if (!userAgent) return Monitor;
  const ua = userAgent.toLowerCase();
  if (ua.includes("iphone") || ua.includes("android") && ua.includes("mobile"))
    return Smartphone;
  if (ua.includes("ipad") || ua.includes("tablet")) return Tablet;
  return Monitor;
}

function formatTime(iso: string | null) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function limitLabel(data: SessionListResponse) {
  if (data.deviceLimitSource === "UNLIMITED") return "Không giới hạn";
  return `${data.sessions.length}/${data.maxDevices} thiết bị`;
}

export default function DevicesPage() {
  const [data, setData] = useState<SessionListResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [revoking, setRevoking] = useState<string | null>(null);
  const [revokingAll, setRevokingAll] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getActiveSessions());
    } catch (err) {
      toast.error(
        err instanceof ApiError ? err.message : "Không tải được danh sách thiết bị."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleRevoke = async (session: SessionResponse) => {
    setRevoking(session.id);
    try {
      await revokeSession(session.id);
      toast.success(`Đã đăng xuất ${session.deviceLabel}`);
      load();
    } catch (err) {
      toast.error(
        err instanceof ApiError ? err.message : "Không đăng xuất được thiết bị."
      );
    } finally {
      setRevoking(null);
    }
  };

  const handleRevokeAll = async () => {
    setRevokingAll(true);
    try {
      await revokeOtherSessions();
      toast.success("Đã đăng xuất tất cả thiết bị khác.");
      load();
    } catch (err) {
      toast.error(
        err instanceof ApiError
          ? err.message
          : "Không đăng xuất được các thiết bị khác."
      );
    } finally {
      setRevokingAll(false);
    }
  };

  const otherSessions = data?.sessions.filter((s) => !s.current) ?? [];

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <SettingsNav />
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-slate-900">
          Thiết bị đang đăng nhập
        </h1>
        {data && (
          <Badge variant="secondary">{limitLabel(data)}</Badge>
        )}
      </div>

      {loading && (
        <p className="text-sm text-slate-500">Đang tải...</p>
      )}

      {!loading && data && (
        <>
          <div className="space-y-3">
            {data.sessions.map((session) => {
              const Icon = deviceIcon(session.userAgent);
              return (
                <div
                  key={session.id}
                  className="card flex items-center gap-4 p-4"
                >
                  <Icon className="h-8 w-8 shrink-0 text-slate-400" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-slate-900">
                        {session.deviceLabel}
                      </span>
                      {session.current && (
                        <Badge variant="default" className="text-xs">
                          Đang dùng
                        </Badge>
                      )}
                    </div>
                    <p className="text-xs text-slate-500">
                      IP: {session.ipAddress ?? "Không rõ"} · Đăng nhập:{" "}
                      {formatTime(session.issuedAt)} · Hoạt động:{" "}
                      {formatTime(session.lastUsedAt)}
                    </p>
                  </div>
                  {!session.current && (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={revoking === session.id}
                      onClick={() => handleRevoke(session)}
                    >
                      <LogOut className="mr-1 h-3.5 w-3.5" />
                      {revoking === session.id ? "Đang xử lý..." : "Đăng xuất"}
                    </Button>
                  )}
                </div>
              );
            })}
          </div>

          {otherSessions.length > 0 && (
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button
                  variant="destructive"
                  className="w-full"
                  disabled={revokingAll}
                >
                  {revokingAll
                    ? "Đang xử lý..."
                    : "Đăng xuất tất cả thiết bị khác"}
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Xác nhận đăng xuất</AlertDialogTitle>
                  <AlertDialogDescription>
                    Tất cả {otherSessions.length} thiết bị khác sẽ bị đăng xuất.
                    Hành động này không thể hoàn tác.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Hủy</AlertDialogCancel>
                  <AlertDialogAction onClick={handleRevokeAll}>
                    Đăng xuất tất cả
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          )}
        </>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Commit**

```bash
git add Fuexam/app/\(app\)/settings/devices/page.tsx \
      Fuexam/components/settings/SettingsNav.tsx
git commit -m "feat(fuexam): add /settings/devices page for session management"
```

---

### Task 8: Fuexam-admin — API client + types + device limit & session sections

**Files:**
- Modify: `Fuexam-admin/types/api.ts`
- Modify: `Fuexam-admin/lib/api/admin.ts`
- Modify: `Fuexam-admin/app/users/[userId]/permissions/page.tsx`

**Interfaces:**
- Consumes: Admin session + device-limit endpoints from Task 5

- [ ] **Step 1: Add TypeScript types to Fuexam-admin**

In `Fuexam-admin/types/api.ts`, add at the end:

```typescript
export interface SessionResponse {
  id: string;
  ipAddress: string | null;
  userAgent: string | null;
  deviceLabel: string;
  issuedAt: string;
  lastUsedAt: string | null;
  current: boolean;
}

export interface SessionListResponse {
  sessions: SessionResponse[];
  maxDevices: number;
  deviceLimitSource: "GLOBAL" | "CUSTOM" | "UNLIMITED";
}
```

- [ ] **Step 2: Add admin API functions**

In `Fuexam-admin/lib/api/admin.ts`, add:

```typescript
import type { AdminOverviewResponse, AdminUserPageResponse, SessionListResponse } from "@/types/api";

export function getUserSessions(userId: string) {
  return apiFetch<SessionListResponse>(`/api/v1/admin/users/${userId}/sessions`);
}

export function revokeUserSession(userId: string, familyId: string) {
  return apiFetch<void>(`/api/v1/admin/users/${userId}/sessions/${familyId}`, {
    method: "DELETE",
  });
}

export function revokeAllUserSessions(userId: string) {
  return apiFetch<void>(`/api/v1/admin/users/${userId}/sessions`, {
    method: "DELETE",
  });
}

export function setUserDeviceLimit(userId: string, maxDevices: number | null) {
  return apiFetch<{ maxDevices: number | null }>(
    `/api/v1/admin/users/${userId}/device-limit`,
    {
      method: "PUT",
      body: JSON.stringify({ maxDevices }),
    }
  );
}
```

Update the existing import line at the top to include `SessionListResponse`.

- [ ] **Step 3: Add device limit + sessions sections to admin user page**

In `Fuexam-admin/app/users/[userId]/permissions/page.tsx`, add the following after the existing imports:

```typescript
import * as adminApi from "@/lib/api/admin";
import type { SessionListResponse, SessionResponse } from "@/types/api";
```

Add state variables inside the component (after the existing state declarations):

```typescript
const canUpdateUser = can(user, "admin.user:update");
const canReadUser = can(user, "admin.user:read");

const [sessions, setSessions] = useState<SessionListResponse | null>(null);
const [sessionsLoading, setSessionsLoading] = useState(false);
const [deviceLimitMode, setDeviceLimitMode] = useState<"global" | "unlimited" | "custom">("global");
const [customLimit, setCustomLimit] = useState("");
const [savingLimit, setSavingLimit] = useState(false);
const [revokingSession, setRevokingSession] = useState<string | null>(null);
const [revokingAll, setRevokingAll] = useState(false);
```

Add a `loadSessions` function:

```typescript
const loadSessions = useCallback(async () => {
  if (!canReadUser) return;
  setSessionsLoading(true);
  try {
    const data = await adminApi.getUserSessions(userId);
    setSessions(data);
    if (data.deviceLimitSource === "UNLIMITED") {
      setDeviceLimitMode("unlimited");
    } else if (data.deviceLimitSource === "CUSTOM") {
      setDeviceLimitMode("custom");
      setCustomLimit(String(data.maxDevices));
    } else {
      setDeviceLimitMode("global");
    }
  } catch (err) {
    toast.error(err instanceof ApiError ? err.message : "Không tải được phiên đăng nhập.");
  } finally {
    setSessionsLoading(false);
  }
}, [userId, canReadUser]);
```

Call `loadSessions()` inside the existing `useEffect` — add it to the `load` callback or add a separate effect:

```typescript
useEffect(() => {
  loadSessions();
}, [loadSessions]);
```

Add handlers:

```typescript
const saveDeviceLimit = async () => {
  setSavingLimit(true);
  try {
    let maxDevices: number | null = null;
    if (deviceLimitMode === "unlimited") maxDevices = 0;
    else if (deviceLimitMode === "custom") maxDevices = Number(customLimit);
    await adminApi.setUserDeviceLimit(userId, maxDevices);
    toast.success("Đã cập nhật giới hạn thiết bị.");
    loadSessions();
  } catch (err) {
    toast.error(err instanceof ApiError ? err.message : "Không lưu được giới hạn.");
  } finally {
    setSavingLimit(false);
  }
};

const handleRevokeSession = async (familyId: string) => {
  setRevokingSession(familyId);
  try {
    await adminApi.revokeUserSession(userId, familyId);
    toast.success("Đã đăng xuất phiên.");
    loadSessions();
  } catch (err) {
    toast.error(err instanceof ApiError ? err.message : "Không đăng xuất được.");
  } finally {
    setRevokingSession(null);
  }
};

const handleRevokeAll = async () => {
  setRevokingAll(true);
  try {
    await adminApi.revokeAllUserSessions(userId);
    toast.success("Đã đăng xuất tất cả phiên.");
    loadSessions();
  } catch (err) {
    toast.error(err instanceof ApiError ? err.message : "Không đăng xuất được.");
  } finally {
    setRevokingAll(false);
  }
};
```

Add JSX sections after the existing Override card (before the Points dialog). Insert inside the `{!loading && ...}` block:

```tsx
{!loading && canReadUser && (
  <Card className="mt-6">
    <CardHeader>
      <CardTitle className="text-base">Giới hạn thiết bị</CardTitle>
    </CardHeader>
    <CardContent>
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label>Chế độ</Label>
          <Select value={deviceLimitMode} onValueChange={(v) => setDeviceLimitMode(v as "global" | "unlimited" | "custom")} disabled={!canUpdateUser}>
            <SelectTrigger className="w-[200px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="global">Mặc định hệ thống ({sessions?.deviceLimitSource === "GLOBAL" ? sessions.maxDevices : "2"})</SelectItem>
              <SelectItem value="unlimited">Không giới hạn</SelectItem>
              <SelectItem value="custom">Tùy chỉnh</SelectItem>
            </SelectContent>
          </Select>
        </div>
        {deviceLimitMode === "custom" && (
          <div className="space-y-1">
            <Label>Số thiết bị tối đa</Label>
            <Input type="number" min={1} max={100} className="w-[100px]" value={customLimit} onChange={(e) => setCustomLimit(e.target.value)} disabled={!canUpdateUser} />
          </div>
        )}
        {canUpdateUser && (
          <Button onClick={saveDeviceLimit} disabled={savingLimit}>
            {savingLimit ? "Đang lưu..." : "Lưu"}
          </Button>
        )}
      </div>
    </CardContent>
  </Card>
)}

{!loading && canReadUser && (
  <Card className="mt-6">
    <CardHeader className="flex flex-row items-center justify-between">
      <CardTitle className="text-base">Phiên đăng nhập ({sessions?.sessions.length ?? 0})</CardTitle>
      {canUpdateUser && sessions && sessions.sessions.length > 0 && (
        <Button variant="destructive" size="sm" onClick={handleRevokeAll} disabled={revokingAll}>
          {revokingAll ? "Đang xử lý..." : "Đăng xuất tất cả"}
        </Button>
      )}
    </CardHeader>
    <CardContent>
      {sessionsLoading && <p className="text-sm text-muted-foreground">Đang tải...</p>}
      {!sessionsLoading && sessions && sessions.sessions.length === 0 && (
        <p className="text-sm text-muted-foreground">Không có phiên đăng nhập nào.</p>
      )}
      {!sessionsLoading && sessions && sessions.sessions.length > 0 && (
        <div className="space-y-2">
          {sessions.sessions.map((s) => (
            <div key={s.id} className="flex items-center justify-between rounded-lg border border-border px-3 py-2">
              <div>
                <p className="text-sm font-medium">{s.deviceLabel}</p>
                <p className="text-xs text-muted-foreground">
                  IP: {s.ipAddress ?? "—"} · {new Date(s.issuedAt).toLocaleString("vi-VN")}
                  {s.lastUsedAt && ` · Hoạt động: ${new Date(s.lastUsedAt).toLocaleString("vi-VN")}`}
                </p>
              </div>
              {canUpdateUser && (
                <Button variant="outline" size="sm" onClick={() => handleRevokeSession(s.id)} disabled={revokingSession === s.id}>
                  {revokingSession === s.id ? "..." : "Đăng xuất"}
                </Button>
              )}
            </div>
          ))}
        </div>
      )}
    </CardContent>
  </Card>
)}
```

- [ ] **Step 4: Commit**

```bash
git add Fuexam-admin/types/api.ts \
      Fuexam-admin/lib/api/admin.ts \
      Fuexam-admin/app/users/\[userId\]/permissions/page.tsx
git commit -m "feat(fuexam-admin): add device limit config and session management to admin panel"
```

---

### Task 9: Verify end-to-end

**Files:** None (verification only)

- [ ] **Step 1: Build backend**

```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Run all backend tests**

```bash
cd backend && mvn -q test
```

Expected: All tests PASS

- [ ] **Step 3: Start backend and verify endpoints**

```bash
cd backend && docker compose up -d postgres redis
cd backend && mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local
```

Test health:
```bash
curl -s http://localhost:8080/actuator/health | jq .status
```

Expected: `"UP"`

- [ ] **Step 4: Build Fuexam frontend**

```bash
cd Fuexam && npm run build
```

Expected: No build errors

- [ ] **Step 5: Build Fuexam-admin frontend**

```bash
cd Fuexam-admin && npm run build
```

Expected: No build errors

- [ ] **Step 6: Commit any final fixes**

If any build/test issues were discovered and fixed, commit them:

```bash
git add -A
git commit -m "fix: address build/test issues from session management feature"
```
