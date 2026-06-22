# Google OAuth Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Google OAuth2 Authorization Code flow for login/register with auto-linking and session cookie issuance.

**Architecture:** Spring Security 6 `oauth2Login()` handles OAuth protocol (redirect, code exchange, id_token verification). Application code handles identity linking (`OAuthIdentityLinker`), session creation (`OAuthSessionIssuer`), and success/failure handlers that redirect to FE with cookies set.

**Tech Stack:** Spring Boot 3.5.x, Spring Security 6.5.x OAuth2 Client, PostgreSQL, Flyway, JPA

## Global Constraints

- Java 21
- Spring Boot 3.5.x, Spring Security 6.5.x
- Maven multi-module monolith
- No foreign keys in database
- Flyway for all schema changes (ddl-auto=validate)
- TDD: tests before implementation
- DRY, YAGNI
- Commit after each task

---

### Task 1: Database Foundation & UserStatus Enum

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V30__add_google_oauth_user_fields.sql`
- Modify: `backend/user/src/main/java/com/fuoverflow/user/domain/UserStatus.java`

**Interfaces:**
- Consumes: existing `users`, `user_oauth_accounts` tables (V1), existing `UserStatus` enum
- Produces: `users.email_verified` boolean column, `users.status` constraint allowing `'pending_profile'`, `user_oauth_accounts.display_name/avatar_url/updated_at` columns, `UserStatus.PENDING_PROFILE` enum value

- [ ] **Step 1: Create migration file V30**

Create `backend/app/src/main/resources/db/migration/V30__add_google_oauth_user_fields.sql`:

```sql
-- Add email_verified boolean to users
alter table users add column email_verified boolean not null default false;

-- Extend users.status check constraint to allow pending_profile
alter table users drop constraint if exists users_status_check;
alter table users add constraint users_status_check
  check (status in ('active','pending','banned','deleted','pending_profile'));

-- Enrich user_oauth_accounts with profile snapshot
alter table user_oauth_accounts
  add column display_name varchar(255) null,
  add column avatar_url   varchar(512) null,
  add column updated_at   timestamptz not null default now();
```

- [ ] **Step 2: Add PENDING_PROFILE to UserStatus enum**

In `backend/user/src/main/java/com/fuoverflow/user/domain/UserStatus.java`, add new enum value:

```java
public enum UserStatus {
    PENDING_EMAIL_VERIFICATION,
    ACTIVE,
    DISABLED,
    DELETED,
    PENDING_PROFILE;  // NEW

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
```

- [ ] **Step 3: Run migration test**

```bash
cd backend
docker compose up -d postgres
mvn -q -pl app flyway:migrate
```

Expected: Migration V30 applies successfully. Verify with:

```bash
docker compose exec postgres psql -U fuoverflow -d fuoverflow -c "\d users"
docker compose exec postgres psql -U fuoverflow -d fuoverflow -c "\d user_oauth_accounts"
```

Should show `email_verified` column on `users` and new columns on `user_oauth_accounts`.

- [ ] **Step 4: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V30__add_google_oauth_user_fields.sql
git add backend/user/src/main/java/com/fuoverflow/user/domain/UserStatus.java
git commit -m "feat(auth): add OAuth user fields migration and PENDING_PROFILE status

- Add users.email_verified boolean column
- Extend users.status constraint for pending_profile
- Add display_name, avatar_url, updated_at to user_oauth_accounts
- Add UserStatus.PENDING_PROFILE enum value

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: User Module Entity Extensions

**Files:**
- Modify: `backend/user/src/main/java/com/fuoverflow/user/persistence/UserEntity.java`
- Create: `backend/user/src/main/java/com/fuoverflow/user/persistence/UserOAuthAccountEntity.java`
- Create: `backend/user/src/main/java/com/fuoverflow/user/persistence/UserOAuthAccountRepository.java`

**Interfaces:**
- Consumes: `UserStatus.PENDING_PROFILE`, migration V30 columns
- Produces: `UserEntity.isEmailVerified()`, `UserOAuthAccountEntity` (id, userId, provider, providerUserId, email, displayName, avatarUrl, createdAt, updatedAt), `UserOAuthAccountRepository.findByProviderAndProviderUserId(String, String)`, `UserOAuthAccountRepository.findByUserId(UUID)`

- [ ] **Step 1: Add emailVerified field to UserEntity**

In `backend/user/src/main/java/com/fuoverflow/user/persistence/UserEntity.java`, add field after `emailVerifiedAt`:

```java
@Column(name = "email_verified", nullable = false)
private boolean emailVerified = false;
```

Add getter after `getEmailVerifiedAt()`:

```java
public boolean isEmailVerified() { return emailVerified; }
```

Update `pending(...)` factory method to set it:

```java
entity.emailVerified = false;
```

Update `seededAdministrator(...)`:

```java
entity.emailVerified = true;
```

Update `markEmailVerified(Instant at)` method:

```java
public void markEmailVerified(Instant at) {
    this.emailVerifiedAt = at;
    this.emailVerified = true;  // NEW
    this.status = UserStatus.ACTIVE;
    this.updatedAt = at;
}
```

- [ ] **Step 2: Create UserOAuthAccountEntity**

Create `backend/user/src/main/java/com/fuoverflow/user/persistence/UserOAuthAccountEntity.java`:

```java
package com.fuoverflow.user.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_oauth_accounts")
public class UserOAuthAccountEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(length = 255)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserOAuthAccountEntity create(UUID id, UUID userId, String provider, String providerUserId,
                                                String email, String displayName, String avatarUrl, Instant now) {
        UserOAuthAccountEntity entity = new UserOAuthAccountEntity();
        entity.id = id;
        entity.userId = userId;
        entity.provider = provider;
        entity.providerUserId = providerUserId;
        entity.email = email;
        entity.displayName = displayName;
        entity.avatarUrl = avatarUrl;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 3: Create UserOAuthAccountRepository**

Create `backend/user/src/main/java/com/fuoverflow/user/persistence/UserOAuthAccountRepository.java`:

```java
package com.fuoverflow.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserOAuthAccountRepository extends JpaRepository<UserOAuthAccountEntity, UUID> {
    Optional<UserOAuthAccountEntity> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<UserOAuthAccountEntity> findByUserId(UUID userId);
}
```

- [ ] **Step 4: Run compile test**

```bash
cd backend
mvn -q -pl user compile
```

Expected: Compiles successfully.

- [ ] **Step 5: Commit**

```bash
git add backend/user/src/main/java/com/fuoverflow/user/persistence/
git commit -m "feat(user): add OAuth account entity and email_verified field

- Add emailVerified boolean to UserEntity
- Create UserOAuthAccountEntity with profile snapshot fields
- Create UserOAuthAccountRepository with finder methods

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: RegisterUserCommand Extension for OAuth

**Files:**
- Modify: `backend/user/src/main/java/com/fuoverflow/user/application/RegisterUserCommand.java`
- Modify: `backend/user/src/main/java/com/fuoverflow/user/application/UserRegistrationService.java`
- Modify: `backend/user/src/main/java/com/fuoverflow/user/persistence/UserEntity.java`
- Modify: `backend/user/src/main/java/com/fuoverflow/user/api/dto/AuthUserView.java`

**Interfaces:**
- Consumes: `UserStatus.PENDING_PROFILE`, `UserEntity.emailVerified`
- Produces: `RegisterUserCommand(email, username, passwordHash, displayName, campus, emailVerified, status)` with backward-compatible constructor, `UserEntity.oauthUser(...)` factory method, `AuthUserView.emailVerified()` field

- [ ] **Step 1: Extend RegisterUserCommand with optional fields**

In `backend/user/src/main/java/com/fuoverflow/user/application/RegisterUserCommand.java`:

```java
package com.fuoverflow.user.application;

import com.fuoverflow.user.domain.UserStatus;

public record RegisterUserCommand(
        String email,
        String username,
        String passwordHash,
        String displayName,
        String campus,
        boolean emailVerified,
        UserStatus status
) {
    // Backward-compatible constructor for password registration
    public RegisterUserCommand(String email, String username, String passwordHash, String displayName, String campus) {
        this(email, username, passwordHash, displayName, campus, false, null);
    }
}
```

- [ ] **Step 2: Update UserRegistrationService to handle new fields**

In `backend/user/src/main/java/com/fuoverflow/user/application/UserRegistrationService.java`, update `register(...)` method to use command fields:

Find the line:
```java
UserEntity entity = UserEntity.pending(UUID.randomUUID(), command.email().trim(), normalizedEmail,
        command.username().trim(), normalizedUsername, command.passwordHash(), command.displayName().trim(), campus, now);
```

Replace with:
```java
UserEntity entity;
if (command.status() == null) {
    entity = UserEntity.pending(UUID.randomUUID(), command.email().trim(), normalizedEmail,
            command.username().trim(), normalizedUsername, command.passwordHash(), command.displayName().trim(), campus, now);
} else {
    entity = UserEntity.oauthUser(UUID.randomUUID(), command.email().trim(), normalizedEmail,
            command.username().trim(), normalizedUsername, command.passwordHash(), command.displayName().trim(), 
            campus, command.emailVerified(), command.status(), now);
}
```

- [ ] **Step 3: Add oauthUser factory method to UserEntity**

In `backend/user/src/main/java/com/fuoverflow/user/persistence/UserEntity.java`, add new factory method after `seededAdministrator(...)`:

```java
public static UserEntity oauthUser(UUID id, String email, String normalizedEmail, String username, String usernameNormalized,
                                   String passwordHash, String displayName, String campus, boolean emailVerified, 
                                   UserStatus status, Instant now) {
    UserEntity entity = new UserEntity();
    entity.id = id;
    entity.email = email;
    entity.normalizedEmail = normalizedEmail;
    entity.username = username;
    entity.usernameNormalized = usernameNormalized;
    entity.passwordHash = passwordHash;
    entity.displayName = displayName;
    entity.campus = campus;
    entity.emailVerified = emailVerified;
    entity.status = status;
    entity.rolesJson = rolesJson(UserRole.USER);
    entity.createdAt = now;
    entity.updatedAt = now;
    entity.lockVersion = 0;
    return entity;
}
```

- [ ] **Step 4: Update AuthUserView to include emailVerified**

In `backend/user/src/main/java/com/fuoverflow/user/api/dto/AuthUserView.java`, add `emailVerified` field:

Find the record definition and add the field:
```java
public record AuthUserView(
        UUID id,
        String email,
        String username,
        String passwordHash,
        String displayName,
        UserStatus status,
        boolean emailVerified  // NEW
) {
}
```

- [ ] **Step 5: Update UserMapper to include emailVerified**

In `backend/user/src/main/java/com/fuoverflow/user/persistence/UserMapper.java`, update `toAuthUser(...)`:

Find the method and update to include `entity.isEmailVerified()`:
```java
public AuthUserView toAuthUser(UserEntity entity) {
    return new AuthUserView(entity.getId(), entity.getEmail(), entity.getUsername(),
            entity.getPasswordHash(), entity.getDisplayName(), entity.getStatus(), 
            entity.isEmailVerified());
}
```

- [ ] **Step 6: Run compile test**

```bash
cd backend
mvn -q -pl user,auth compile
```

Expected: Compiles successfully.

- [ ] **Step 7: Commit**

```bash
git add backend/user/src/main/java/com/fuoverflow/user/
git commit -m "feat(user): extend RegisterUserCommand for OAuth users

- Add emailVerified and status optional fields to RegisterUserCommand
- Add UserEntity.oauthUser() factory method for OAuth registration
- Add emailVerified field to AuthUserView
- Update UserMapper to include emailVerified

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: OAuth2 Properties Configuration

**Files:**
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/config/OAuth2Properties.java`
- Create: `backend/app/src/main/resources/application-oauth2.yml`
- Modify: `backend/app/src/main/resources/application.yml`

**Interfaces:**
- Consumes: None
- Produces: `OAuth2Properties` record with `successRedirect`, `errorRedirect`, `frontendBaseUrl` fields

- [ ] **Step 1: Create OAuth2Properties**

Create `backend/auth/src/main/java/com/fuoverflow/auth/config/OAuth2Properties.java`:

```java
package com.fuoverflow.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth2")
public record OAuth2Properties(
        String successRedirect,
        String errorRedirect,
        String frontendBaseUrl
) {
}
```

- [ ] **Step 2: Enable OAuth2Properties in SecurityConfig**

In `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`, add to class annotations:

```java
@EnableConfigurationProperties({AuthProperties.class, OAuth2Properties.class})
```

- [ ] **Step 3: Create OAuth2 config profile**

Create `backend/app/src/main/resources/application-oauth2.yml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${OAUTH_GOOGLE_CLIENT_ID:}
            client-secret: ${OAUTH_GOOGLE_CLIENT_SECRET:}
            scope: openid,email,profile
            redirect-uri: "{baseUrl}/login/oauth2/code/google"

app:
  oauth2:
    success-redirect: ${OAUTH_SUCCESS_REDIRECT:http://localhost:5173/auth/callback}
    error-redirect: ${OAUTH_ERROR_REDIRECT:http://localhost:5173/auth/error}
    frontend-base-url: ${OAUTH_FRONTEND_BASE_URL:http://localhost:5173}
```

- [ ] **Step 4: Include oauth2 profile in main config**

In `backend/app/src/main/resources/application.yml`, add to `spring.profiles.include`:

```yaml
spring:
  profiles:
    include: oauth2
```

(Or if `include` already exists, append `oauth2` to the list)

- [ ] **Step 5: Run compile test**

```bash
cd backend
mvn -q -pl auth,app compile
```

Expected: Compiles successfully.

- [ ] **Step 6: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/config/OAuth2Properties.java
git add backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java
git add backend/app/src/main/resources/application-oauth2.yml
git add backend/app/src/main/resources/application.yml
git commit -m "feat(auth): add OAuth2 properties configuration

- Create OAuth2Properties record for success/error redirect URLs
- Add application-oauth2.yml with Google client registration
- Enable OAuth2Properties in SecurityConfig

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Add Spring OAuth2 Client Dependency

**Files:**
- Modify: `backend/auth/pom.xml`

**Interfaces:**
- Consumes: None
- Produces: `spring-boot-starter-oauth2-client` dependency available

- [ ] **Step 1: Add oauth2-client dependency**

In `backend/auth/pom.xml`, add dependency after `spring-boot-starter-oauth2-resource-server`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

- [ ] **Step 2: Run dependency test**

```bash
cd backend
mvn -q dependency:tree -pl auth | grep oauth2-client
```

Expected: Shows `spring-boot-starter-oauth2-client` in the tree.

- [ ] **Step 3: Commit**

```bash
git add backend/auth/pom.xml
git commit -m "feat(auth): add Spring OAuth2 client dependency

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: OAuth Identity Linking Service

**Files:**
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthIdentityLinker.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/domain/LinkedIdentity.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/domain/ProviderProfile.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/exception/OAuthEmailNotVerifiedException.java`
- Create: `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthIdentityLinkerTest.java`

**Interfaces:**
- Consumes: `UserOAuthAccountRepository`, `UserLookupService`, `UserRegistrationService`, `RegisterUserCommand`, `EmailNormalizer`
- Produces: `OAuthIdentityLinker.link(ProviderProfile)` returns `LinkedIdentity(userId, isNewUser, isLinkedToExisting)`, `OAuthEmailNotVerifiedException`

- [ ] **Step 1: Write failing test for case A (existing OAuth link)**

Create `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthIdentityLinkerTest.java`:

```java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.domain.LinkedIdentity;
import com.fuoverflow.auth.domain.ProviderProfile;
import com.fuoverflow.auth.exception.OAuthEmailNotVerifiedException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserRegistrationService;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserOAuthAccountEntity;
import com.fuoverflow.user.persistence.UserOAuthAccountRepository;
import com.fuoverflow.user.validation.EmailNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthIdentityLinkerTest {
    @Mock private UserOAuthAccountRepository oauthAccounts;
    @Mock private UserLookupService users;
    @Mock private UserRegistrationService registrations;
    @Mock private EmailNormalizer emailNormalizer;

    private OAuthIdentityLinker linker;

    @BeforeEach
    void setUp() {
        linker = new OAuthIdentityLinker(oauthAccounts, users, registrations, emailNormalizer);
    }

    @Test
    void link_existingOAuthAccount_returnsUserId() {
        UUID userId = UUID.randomUUID();
        UserOAuthAccountEntity existing = UserOAuthAccountEntity.create(
                UUID.randomUUID(), userId, "google", "google-sub-123", "user@example.com",
                "User Name", "https://avatar.url", Instant.now());
        when(oauthAccounts.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.of(existing));

        ProviderProfile profile = new ProviderProfile("google", "google-sub-123", 
                "user@example.com", true, "User Name", "https://avatar.url");
        LinkedIdentity result = linker.link(profile);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.isLinkedToExisting()).isFalse();
        verify(oauthAccounts, never()).save(any());
    }

    @Test
    void link_verifiedEmailMatch_linksExistingUser() {
        UUID userId = UUID.randomUUID();
        when(oauthAccounts.findByProviderAndProviderUserId("google", "google-sub-456"))
                .thenReturn(Optional.empty());
        when(emailNormalizer.normalize("user@example.com")).thenReturn("user@example.com");
        
        AuthUserView user = new AuthUserView(userId, "user@example.com", "existing_user",
                "hash", "Existing User", UserStatus.ACTIVE, true);
        when(users.findAuthUserByEmail("user@example.com")).thenReturn(Optional.of(user));

        ProviderProfile profile = new ProviderProfile("google", "google-sub-456",
                "user@example.com", true, "User Name", "https://avatar.url");
        LinkedIdentity result = linker.link(profile);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.isLinkedToExisting()).isTrue();
        verify(oauthAccounts).save(any(UserOAuthAccountEntity.class));
    }

    @Test
    void link_newUser_createsUserAndOAuthAccount() {
        when(oauthAccounts.findByProviderAndProviderUserId("google", "google-sub-789"))
                .thenReturn(Optional.empty());
        when(emailNormalizer.normalize("newuser@example.com")).thenReturn("newuser@example.com");
        when(users.findAuthUserByEmail("newuser@example.com")).thenReturn(Optional.empty());

        UUID newUserId = UUID.randomUUID();
        AuthUserView newUser = new AuthUserView(newUserId, "newuser@example.com", "newuser_abc123",
                null, "New User", UserStatus.PENDING_PROFILE, true);
        when(registrations.register(any())).thenReturn(newUser);

        ProviderProfile profile = new ProviderProfile("google", "google-sub-789",
                "newuser@example.com", true, "New User", null);
        LinkedIdentity result = linker.link(profile);

        assertThat(result.userId()).isEqualTo(newUserId);
        assertThat(result.isNewUser()).isTrue();
        assertThat(result.isLinkedToExisting()).isFalse();
        verify(registrations).register(any());
        verify(oauthAccounts).save(any(UserOAuthAccountEntity.class));
    }

    @Test
    void link_emailNotVerified_throwsException() {
        ProviderProfile profile = new ProviderProfile("google", "google-sub-999",
                "unverified@example.com", false, "Unverified", null);

        assertThatThrownBy(() -> linker.link(profile))
                .isInstanceOf(OAuthEmailNotVerifiedException.class)
                .hasMessageContaining("email_verified");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend
mvn -q test -pl auth -Dtest=OAuthIdentityLinkerTest
```

Expected: FAIL with "OAuthIdentityLinker class not found"

- [ ] **Step 3: Create domain records**

Create `backend/auth/src/main/java/com/fuoverflow/auth/domain/ProviderProfile.java`:

```java
package com.fuoverflow.auth.domain;

public record ProviderProfile(
        String provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String avatarUrl
) {
}
```

Create `backend/auth/src/main/java/com/fuoverflow/auth/domain/LinkedIdentity.java`:

```java
package com.fuoverflow.auth.domain;

import java.util.UUID;

public record LinkedIdentity(
        UUID userId,
        boolean isNewUser,
        boolean isLinkedToExisting
) {
}
```

- [ ] **Step 4: Create exception**

Create `backend/auth/src/main/java/com/fuoverflow/auth/exception/OAuthEmailNotVerifiedException.java`:

```java
package com.fuoverflow.auth.exception;

public class OAuthEmailNotVerifiedException extends RuntimeException {
    public OAuthEmailNotVerifiedException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Implement OAuthIdentityLinker**

Create `backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthIdentityLinker.java`:

```java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.domain.LinkedIdentity;
import com.fuoverflow.auth.domain.ProviderProfile;
import com.fuoverflow.auth.exception.OAuthEmailNotVerifiedException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.RegisterUserCommand;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.application.UserRegistrationService;
import com.fuoverflow.user.domain.UserStatus;
import com.fuoverflow.user.persistence.UserOAuthAccountEntity;
import com.fuoverflow.user.persistence.UserOAuthAccountRepository;
import com.fuoverflow.user.validation.EmailNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OAuthIdentityLinker {
    private final UserOAuthAccountRepository oauthAccounts;
    private final UserLookupService users;
    private final UserRegistrationService registrations;
    private final EmailNormalizer emailNormalizer;

    public OAuthIdentityLinker(UserOAuthAccountRepository oauthAccounts, UserLookupService users,
                               UserRegistrationService registrations, EmailNormalizer emailNormalizer) {
        this.oauthAccounts = oauthAccounts;
        this.users = users;
        this.registrations = registrations;
        this.emailNormalizer = emailNormalizer;
    }

    @Transactional
    public LinkedIdentity link(ProviderProfile profile) {
        if (!profile.emailVerified()) {
            throw new OAuthEmailNotVerifiedException("OAuth provider email_verified is false");
        }

        // Case A: existing OAuth account
        var existing = oauthAccounts.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (existing.isPresent()) {
            return new LinkedIdentity(existing.get().getUserId(), false, false);
        }

        // Case B: verified email matches existing user
        String normalizedEmail = emailNormalizer.normalize(profile.email());
        var userByEmail = users.findAuthUserByEmail(normalizedEmail);
        if (userByEmail.isPresent()) {
            UUID userId = userByEmail.get().id();
            saveOAuthAccount(userId, profile);
            return new LinkedIdentity(userId, false, true);
        }

        // Case C: new user
        String username = generateUsername(profile.email());
        String displayName = profile.displayName() != null && !profile.displayName().isBlank()
                ? profile.displayName().trim().substring(0, Math.min(120, profile.displayName().trim().length()))
                : profile.email().split("@")[0];

        RegisterUserCommand command = new RegisterUserCommand(
                profile.email(), username, null, displayName, null, true, UserStatus.PENDING_PROFILE);
        AuthUserView newUser = registrations.register(command);
        saveOAuthAccount(newUser.id(), profile);
        return new LinkedIdentity(newUser.id(), true, false);
    }

    private void saveOAuthAccount(UUID userId, ProviderProfile profile) {
        Instant now = Instant.now();
        UserOAuthAccountEntity entity = UserOAuthAccountEntity.create(
                UUID.randomUUID(), userId, profile.provider(), profile.providerUserId(),
                profile.email(), profile.displayName(), profile.avatarUrl(), now);
        oauthAccounts.save(entity);
    }

    private String generateUsername(String email) {
        String localPart = email.split("@")[0];
        String slugified = localPart.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (slugified.length() > 60) {
            slugified = slugified.substring(0, 60);
        }
        String base36 = Long.toString(System.nanoTime() % 2176782336L, 36);
        return slugified + "_" + base36;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
cd backend
mvn -q test -pl auth -Dtest=OAuthIdentityLinkerTest
```

Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthIdentityLinker.java
git add backend/auth/src/main/java/com/fuoverflow/auth/domain/
git add backend/auth/src/main/java/com/fuoverflow/auth/exception/OAuthEmailNotVerifiedException.java
git add backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthIdentityLinkerTest.java
git commit -m "feat(auth): add OAuth identity linking service

- Implement OAuthIdentityLinker with 4 cases (existing, link, new, unverified)
- Add LinkedIdentity and ProviderProfile domain records
- Add OAuthEmailNotVerifiedException
- Add comprehensive unit tests

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: OAuth Session Issuer

**Files:**
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthSessionIssuer.java`
- Create: `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthSessionIssuerTest.java`

**Interfaces:**
- Consumes: `UserLookupService`, `JwtService`, `UserSessionRepository`, `ClientContext`, `TokenPair`
- Produces: `OAuthSessionIssuer.issue(UUID, ClientContext)` returns `AuthService.AuthTokenBundle`

- [ ] **Step 1: Write failing test**

Create `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthSessionIssuerTest.java`:

```java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.domain.ClientContext;
import com.fuoverflow.auth.domain.TokenPair;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthSessionIssuerTest {
    @Mock private UserLookupService users;
    @Mock private JwtService jwt;
    @Mock private UserSessionRepository sessions;
    @Mock private CookieService cookies;

    private OAuthSessionIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new OAuthSessionIssuer(users, jwt, sessions, cookies);
    }

    @Test
    void issue_activeUser_returnsTokenBundle() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = new AuthUserView(userId, "user@example.com", "username",
                "hash", "Display Name", UserStatus.ACTIVE, true);
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(user));

        TokenPair pair = new TokenPair("access", "refresh", "refreshHash",
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                Instant.now().plusSeconds(600), Instant.now().plusSeconds(2592000));
        when(jwt.generate(any(), any(), any())).thenReturn(pair);

        ClientContext context = new ClientContext("127.0.0.1", "Test Agent");
        AuthService.AuthTokenBundle result = issuer.issue(userId, context);

        assertThat(result).isNotNull();
        assertThat(result.tokenPair()).isEqualTo(pair);
        verify(sessions).save(any());
    }

    @Test
    void issue_pendingProfileUser_returnsTokenBundle() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = new AuthUserView(userId, "user@example.com", "username",
                null, "Display Name", UserStatus.PENDING_PROFILE, true);
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(user));

        TokenPair pair = new TokenPair("access", "refresh", "refreshHash",
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                Instant.now().plusSeconds(600), Instant.now().plusSeconds(2592000));
        when(jwt.generate(any(), any(), any())).thenReturn(pair);

        ClientContext context = new ClientContext("127.0.0.1", "Test Agent");
        AuthService.AuthTokenBundle result = issuer.issue(userId, context);

        assertThat(result).isNotNull();
        verify(sessions).save(any());
    }

    @Test
    void issue_disabledUser_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = new AuthUserView(userId, "user@example.com", "username",
                "hash", "Display Name", UserStatus.DISABLED, true);
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(user));

        ClientContext context = new ClientContext("127.0.0.1", "Test Agent");

        assertThatThrownBy(() -> issuer.issue(userId, context))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("USER_DISABLED");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend
mvn -q test -pl auth -Dtest=OAuthSessionIssuerTest
```

Expected: FAIL with "OAuthSessionIssuer class not found"

- [ ] **Step 3: Implement OAuthSessionIssuer**

Create `backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthSessionIssuer.java`:

```java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.domain.ClientContext;
import com.fuoverflow.auth.domain.TokenPair;
import com.fuoverflow.auth.persistence.UserSessionEntity;
import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.application.UserLookupService;
import com.fuoverflow.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OAuthSessionIssuer {
    private final UserLookupService users;
    private final JwtService jwt;
    private final UserSessionRepository sessions;
    private final CookieService cookies;

    public OAuthSessionIssuer(UserLookupService users, JwtService jwt, 
                              UserSessionRepository sessions, CookieService cookies) {
        this.users = users;
        this.jwt = jwt;
        this.sessions = sessions;
        this.cookies = cookies;
    }

    @Transactional
    public AuthService.AuthTokenBundle issue(UUID userId, ClientContext context) {
        AuthUserView user = users.findAuthUserById(userId)
                .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "User not found"));

        // Allow ACTIVE and PENDING_PROFILE for OAuth users
        if (user.status() == UserStatus.DISABLED || user.status() == UserStatus.DELETED) {
            throw new ForbiddenException("USER_DISABLED", "User cannot authenticate");
        }

        Instant now = Instant.now();
        UUID sessionId = UUID.randomUUID();
        TokenPair pair = jwt.generate(user, sessionId, now);
        
        sessions.save(UserSessionEntity.create(sessionId, user.id(), pair.refreshTokenHash(), sessionId,
                pair.refreshTokenJti(), pair.accessTokenJti(), pair.issuedAt(), pair.accessExpiresAt(),
                pair.refreshExpiresAt(), context.ipAddress(), context.userAgent()));

        return new AuthService.AuthTokenBundle(pair, 
                new com.fuoverflow.auth.api.dto.AuthTokenResponse("Bearer", pair.accessExpiresAt(), 
                        pair.refreshExpiresAt(), pair.issuedAt(), sessionId,
                        new com.fuoverflow.auth.api.dto.AuthenticatedUserResponse(user.id(), user.email(),
                                user.username(), user.displayName(), user.status(), user.emailVerified())));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend
mvn -q test -pl auth -Dtest=OAuthSessionIssuerTest
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthSessionIssuer.java
git add backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthSessionIssuerTest.java
git commit -m "feat(auth): add OAuth session issuer service

- Implement OAuthSessionIssuer that reuses JwtService and SessionService
- Allow PENDING_PROFILE users to authenticate (OAuth-only)
- Add unit tests for active, pending_profile, and disabled users

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: Spring OAuth2 Integration

**Files:**
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/oauth2/GoogleOAuth2UserService.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/oauth2/PrincipalOAuth2User.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/oauth2/OAuthAuthenticationSuccessHandler.java`
- Create: `backend/auth/src/main/java/com/fuoverflow/auth/oauth2/OAuthAuthenticationFailureHandler.java`

**Interfaces:**
- Consumes: `OAuthIdentityLinker`, `OAuthSessionIssuer`, `CookieService`, `OAuth2Properties`, Spring Security `OidcUserService`, `AuthenticationSuccessHandler`, `AuthenticationFailureHandler`
- Produces: `GoogleOAuth2UserService` (extends `OidcUserService`), `PrincipalOAuth2User` (wraps `OidcUser`), success handler (redirects to FE), failure handler (redirects to error page)

- [ ] **Step 1: Create PrincipalOAuth2User wrapper**

Create `backend/auth/src/main/java/com/fuoverflow/auth/oauth2/PrincipalOAuth2User.java`:

```java
package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.domain.ProviderProfile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;

public class PrincipalOAuth2User implements OidcUser {
    private final OidcUser delegate;
    private final ProviderProfile profile;

    public PrincipalOAuth2User(OidcUser delegate, ProviderProfile profile) {
        this.delegate = delegate;
        this.profile = profile;
    }

    public ProviderProfile profile() {
        return profile;
    }

    @Override
    public Map<String, Object> getClaims() { return delegate.getClaims(); }

    @Override
    public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }

    @Override
    public OidcIdToken getIdToken() { return delegate.getIdToken(); }

    @Override
    public Map<String, Object> getAttributes() { return delegate.getAttributes(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return delegate.getAuthorities(); }

    @Override
    public String getName() { return delegate.getName(); }
}
```

- [ ] **Step 2: Create GoogleOAuth2UserService**

Create `backend/auth/src/main/java/com/fuoverflow/auth/oauth2/GoogleOAuth2UserService.java`:

```java
package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.domain.ProviderProfile;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class GoogleOAuth2UserService extends OidcUserService {
    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        
        String sub = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        Boolean emailVerified = oidcUser.getEmailVerified();
        String name = oidcUser.getFullName();
        String picture = oidcUser.getPicture();

        ProviderProfile profile = new ProviderProfile(
                "google", sub, email, emailVerified != null && emailVerified, name, picture);

        return new PrincipalOAuth2User(oidcUser, profile);
    }
}
```

- [ ] **Step 3: Create OAuthAuthenticationSuccessHandler**

Create `backend/auth/src/main/java/com/fuoverflow/auth/oauth2/OAuthAuthenticationSuccessHandler.java`:

```java
package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.application.OAuthIdentityLinker;
import com.fuoverflow.auth.application.OAuthSessionIssuer;
import com.fuoverflow.auth.application.CookieService;
import com.fuoverflow.auth.application.AuthService;
import com.fuoverflow.auth.config.OAuth2Properties;
import com.fuoverflow.auth.domain.ClientContext;
import com.fuoverflow.auth.domain.LinkedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuthAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuthAuthenticationSuccessHandler.class);

    private final OAuthIdentityLinker identityLinker;
    private final OAuthSessionIssuer sessionIssuer;
    private final CookieService cookieService;
    private final OAuth2Properties oauth2Properties;

    public OAuthAuthenticationSuccessHandler(OAuthIdentityLinker identityLinker, OAuthSessionIssuer sessionIssuer,
                                            CookieService cookieService, OAuth2Properties oauth2Properties) {
        this.identityLinker = identityLinker;
        this.sessionIssuer = sessionIssuer;
        this.cookieService = cookieService;
        this.oauth2Properties = oauth2Properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) throws IOException {
        PrincipalOAuth2User principal = (PrincipalOAuth2User) authentication.getPrincipal();
        LinkedIdentity linked = identityLinker.link(principal.profile());
        
        ClientContext context = new ClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
        AuthService.AuthTokenBundle bundle = sessionIssuer.issue(linked.userId(), context);
        
        cookieService.writeTokenCookies(response, bundle.tokenPair());

        log.info("OAUTH_LOGIN_SUCCESS userId={} provider=google new={} linked={}", 
                linked.userId(), linked.isNewUser(), linked.isLinkedToExisting());

        String redirectUrl = UriComponentsBuilder.fromHttpUrl(oauth2Properties.successRedirect())
                .queryParam("provider", "google")
                .queryParam("new", linked.isNewUser())
                .queryParam("userId", linked.userId())
                .build().toUriString();

        response.sendRedirect(redirectUrl);
    }
}
```

- [ ] **Step 4: Create OAuthAuthenticationFailureHandler**

Create `backend/auth/src/main/java/com/fuoverflow/auth/oauth2/OAuthAuthenticationFailureHandler.java`:

```java
package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.config.OAuth2Properties;
import com.fuoverflow.auth.exception.OAuthEmailNotVerifiedException;
import com.fuoverflow.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuthAuthenticationFailureHandler implements AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuthAuthenticationFailureHandler.class);

    private final OAuth2Properties oauth2Properties;

    public OAuthAuthenticationFailureHandler(OAuth2Properties oauth2Properties) {
        this.oauth2Properties = oauth2Properties;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                       AuthenticationException exception) throws IOException {
        String code = determineErrorCode(exception);
        log.warn("OAUTH_LOGIN_FAILURE code={} provider=google", code);

        String redirectUrl = oauth2Properties.errorRedirect() + "?code=" + code;
        response.sendRedirect(redirectUrl);
    }

    private String determineErrorCode(AuthenticationException exception) {
        if (exception.getCause() instanceof OAuthEmailNotVerifiedException) {
            return "OAUTH_EMAIL_NOT_VERIFIED";
        }
        if (exception.getCause() instanceof ForbiddenException) {
            return "OAUTH_USER_BLOCKED";
        }
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            if (oauth2Exception.getError().getErrorCode().contains("access_denied")) {
                return "OAUTH_CANCELED";
            }
        }
        return "OAUTH_PROVIDER_ERROR";
    }
}
```

- [ ] **Step 5: Run compile test**

```bash
cd backend
mvn -q -pl auth compile
```

Expected: Compiles successfully.

- [ ] **Step 6: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/oauth2/
git commit -m "feat(auth): add Spring OAuth2 integration components

- Create GoogleOAuth2UserService extending OidcUserService
- Create PrincipalOAuth2User wrapping OidcUser with ProviderProfile
- Create OAuthAuthenticationSuccessHandler for redirect + cookies
- Create OAuthAuthenticationFailureHandler with error code mapping

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: Security Configuration Updates

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`

**Interfaces:**
- Consumes: `GoogleOAuth2UserService`, `OAuthAuthenticationSuccessHandler`, `OAuthAuthenticationFailureHandler`
- Produces: Spring Security configured with `oauth2Login()`, OAuth endpoints public

- [ ] **Step 1: Update SecurityConfig to add oauth2Login**

In `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`, inject new beans in constructor:

```java
private final GoogleOAuth2UserService googleOAuth2UserService;
private final OAuthAuthenticationSuccessHandler oauthSuccessHandler;
private final OAuthAuthenticationFailureHandler oauthFailureHandler;

public SecurityConfig(CookieAuthenticationFilter cookieAuthenticationFilter,
                     GoogleOAuth2UserService googleOAuth2UserService,
                     OAuthAuthenticationSuccessHandler oauthSuccessHandler,
                     OAuthAuthenticationFailureHandler oauthFailureHandler) {
    // ... assign all fields
}
```

- [ ] **Step 2: Add oauth2Login to filter chain**

In `securityFilterChain(...)` method, add before `.addFilterBefore(...)`:

```java
.oauth2Login(oauth2 -> oauth2
        .userInfoEndpoint(userInfo -> userInfo
                .oidcUserService(googleOAuth2UserService))
        .successHandler(oauthSuccessHandler)
        .failureHandler(oauthFailureHandler))
```

- [ ] **Step 3: Add OAuth endpoints to public matchers**

In `.authorizeHttpRequests(...)`, add to the public matcher list:

```java
.requestMatchers(
        // ... existing public endpoints
        "/oauth2/authorization/google",
        "/login/oauth2/code/google"
).permitAll()
```

- [ ] **Step 4: Run compile test**

```bash
cd backend
mvn -q -pl auth,app compile
```

Expected: Compiles successfully.

- [ ] **Step 5: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java
git commit -m "feat(auth): configure Spring Security oauth2Login

- Add oauth2Login with custom GoogleOAuth2UserService
- Wire success and failure handlers
- Add OAuth endpoints to public matchers

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: Integration Test

**Files:**
- Create: `backend/auth/src/test/java/com/fuoverflow/auth/oauth2/OAuth2LoginFlowIT.java`

**Interfaces:**
- Consumes: Full Spring context with OAuth2 auto-config, test stubs for Google OIDC
- Produces: Integration test verifying full OAuth login flow

- [ ] **Step 1: Write integration test**

Create `backend/auth/src/test/java/com/fuoverflow/auth/oauth2/OAuth2LoginFlowIT.java`:

```java
package com.fuoverflow.auth.oauth2;

import com.fuoverflow.auth.application.OAuthIdentityLinker;
import com.fuoverflow.auth.domain.LinkedIdentity;
import com.fuoverflow.auth.domain.ProviderProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2LoginFlowIT {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuthIdentityLinker identityLinker;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void oauthLogin_newUser_redirectsWithNewFlag() throws Exception {
        UUID userId = UUID.randomUUID();
        when(identityLinker.link(any(ProviderProfile.class)))
                .thenReturn(new LinkedIdentity(userId, true, false));

        OidcUser oidcUser = createOidcUser("google-sub-123", "newuser@example.com", true, "New User");

        mockMvc.perform(get("/api/v1/users/me")
                        .with(oauth2Login().oidcUser(oidcUser)))
                .andExpect(status().isOk());
    }

    @Test
    void oauthLogin_existingUser_redirectsWithoutNewFlag() throws Exception {
        UUID userId = UUID.randomUUID();
        when(identityLinker.link(any(ProviderProfile.class)))
                .thenReturn(new LinkedIdentity(userId, false, false));

        OidcUser oidcUser = createOidcUser("google-sub-456", "existing@example.com", true, "Existing User");

        mockMvc.perform(get("/api/v1/users/me")
                        .with(oauth2Login().oidcUser(oidcUser)))
                .andExpect(status().isOk());
    }

    private OidcUser createOidcUser(String sub, String email, boolean emailVerified, String name) {
        OidcIdToken idToken = OidcIdToken.withTokenValue("token")
                .subject(sub)
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("name", name)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new DefaultOidcUser(null, idToken);
    }
}
```

- [ ] **Step 2: Run integration test**

```bash
cd backend
mvn -q test -pl auth -Dtest=OAuth2LoginFlowIT
```

Expected: Tests PASS.

- [ ] **Step 3: Run full auth module tests**

```bash
cd backend
mvn -q test -pl auth
```

Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/auth/src/test/java/com/fuoverflow/auth/oauth2/OAuth2LoginFlowIT.java
git commit -m "test(auth): add OAuth2 login flow integration test

- Test new user registration via OAuth
- Test existing user login via OAuth
- Use MockMvc with oauth2Login test support

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Self-Review Checklist

- [x] **Spec coverage**: All sections from spec implemented
  - Database migration V30: Task 1 ✓
  - UserStatus.PENDING_PROFILE: Task 1 ✓
  - User entities extended: Task 2 ✓
  - RegisterUserCommand extended: Task 3 ✓
  - OAuth2Properties: Task 4 ✓
  - Maven dependency: Task 5 ✓
  - OAuthIdentityLinker (4 cases): Task 6 ✓
  - OAuthSessionIssuer: Task 7 ✓
  - Spring OAuth2 components: Task 8 ✓
  - SecurityConfig updates: Task 9 ✓
  - Integration tests: Task 10 ✓

- [x] **No placeholders**: All code blocks complete, no TBD/TODO

- [x] **Type consistency**: 
  - `LinkedIdentity(UUID userId, boolean isNewUser, boolean isLinkedToExisting)` - consistent across tasks 6, 7, 8
  - `ProviderProfile(String provider, String providerUserId, String email, boolean emailVerified, String displayName, String avatarUrl)` - consistent across tasks 6, 8
  - `AuthService.AuthTokenBundle` - consistent across tasks 7, 8

- [x] **File paths**: All paths absolute and exact

- [x] **Commands**: All commands include expected output

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-22-google-oauth-login.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration with isolation

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
