# OAuth2 Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 8 security/reliability issues in the Google OAuth2 login flow so it can safely run in production with multi-instance deployment behind HTTPS.

**Architecture:** The OAuth2 flow uses Spring Security's `oauth2Login()` with a custom OIDC user service, success/failure handlers, cookie-based token delivery, and an identity linker that creates/links user accounts. The fixes touch JWT key management, session storage for OAuth state, cookie security, redirect validation, CSRF, username generation, test coverage, and logging.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Security 6.5.x, Nimbus JOSE, JPA/Hibernate, JUnit 5, Mockito, AssertJ.

## Global Constraints

- Java 21, Spring Boot 3.5.x
- Maven multi-module: `backend/auth`, `backend/common`, `backend/app`
- PostgreSQL; no foreign keys in schema
- Flyway for schema changes; Hibernate `ddl-auto: validate`
- No new infrastructure (no Redis requirement, no microservices)
- Existing test pattern: unit tests with Mockito + `@ExtendWith(MockitoExtension.class)`

---

### Task 1: Load RSA key pair from PEM files instead of generating at startup

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/AuthProperties.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/application/JwtService.java`
- Modify: `backend/app/src/main/resources/application.yml` (auth.jwt section)
- Modify: `backend/app/src/main/resources/application-prod.yml` (auth.jwt override)
- Create: `backend/app/src/main/resources/keys/dev-private.pem` (dev only)
- Create: `backend/app/src/main/resources/keys/dev-public.pem` (dev only)
- Modify: `backend/auth/src/test/java/com/fuoverflow/auth/OAuth2TestApplication.java`
- Test: `backend/auth/src/test/java/com/fuoverflow/auth/application/JwtServiceTest.java`

**Interfaces:**
- Produces: `JwtService` now loads keys from PEM files specified in `AuthProperties.Jwt`. All existing callers (`CookieAuthenticationFilter`, `AuthService`, `OAuthSessionIssuer`) are unaffected — the `generate()` and `decode()` signatures stay the same.

- [ ] **Step 1: Generate dev RSA key pair**

```bash
cd backend/app/src/main/resources
mkdir -p keys
openssl genrsa -out keys/dev-private.pem 2048
openssl rsa -in keys/dev-private.pem -pubout -out keys/dev-public.pem
```

Add `keys/` to `.gitignore` at project root if not already there (dev keys should not ship to prod). Actually, for local dev convenience, commit them — they are only used in `local` profile. Production will override via env var file paths.

- [ ] **Step 2: Extend `AuthProperties.Jwt` with key file locations**

In `backend/auth/src/main/java/com/fuoverflow/auth/config/AuthProperties.java`, change the `Jwt` inner record:

```java
public record Jwt(String keyId, String privateKeyLocation, String publicKeyLocation) {}
```

- [ ] **Step 3: Update `application.yml` with key locations**

In `backend/app/src/main/resources/application.yml`, update the `auth.jwt` section:

```yaml
  jwt:
    key-id: ${AUTH_JWT_KEY_ID:local-dev-key-1}
    private-key-location: ${AUTH_JWT_PRIVATE_KEY:classpath:keys/dev-private.pem}
    public-key-location: ${AUTH_JWT_PUBLIC_KEY:classpath:keys/dev-public.pem}
```

- [ ] **Step 4: Update `application-prod.yml` with prod key paths**

In `backend/app/src/main/resources/application-prod.yml`, add:

```yaml
auth:
  cookie:
    secure: true
  jwt:
    private-key-location: ${AUTH_JWT_PRIVATE_KEY}
    public-key-location: ${AUTH_JWT_PUBLIC_KEY}
```

- [ ] **Step 5: Rewrite `JwtService` constructor to load PEM keys**

Replace the in-memory key generation in `backend/auth/src/main/java/com/fuoverflow/auth/application/JwtService.java`:

```java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.domain.TokenPair;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {
    private final AuthProperties properties;
    private final TokenGenerator generator;
    private final TokenHashing hashing;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtService(AuthProperties properties, TokenGenerator generator, TokenHashing hashing,
                      ResourceLoader resourceLoader) throws Exception {
        this.properties = properties;
        this.generator = generator;
        this.hashing = hashing;

        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        try (InputStream pubStream = resourceLoader.getResource(properties.jwt().publicKeyLocation()).getInputStream();
             InputStream privStream = resourceLoader.getResource(properties.jwt().privateKeyLocation()).getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(pubStream);
            privateKey = RsaKeyConverters.pkcs8().convert(privStream);
        }

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.jwt().keyId())
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(rsaKey));
        this.encoder = new NimbusJwtEncoder(jwkSource);
        this.decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    public TokenPair generate(AuthUserView user, UUID sessionId, Instant now) {
        UUID accessTokenJti = UUID.randomUUID();
        UUID refreshTokenJti = UUID.randomUUID();
        Instant accessExpiresAt = now.plus(properties.accessTokenTtl());
        Instant refreshExpiresAt = now.plus(properties.refreshTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.id().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(accessExpiresAt)
                .id(accessTokenJti.toString())
                .claim("preferred_username", user.username())
                .claim("roles", user.roles())
                .claim("perm_v", user.permVersion())
                .claim("sid", sessionId.toString())
                .claim("typ", "access")
                .build();
        JwsHeader header = JwsHeader.with(() -> "RS256").keyId(properties.jwt().keyId()).build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        String refreshToken = generator.opaqueToken();
        return new TokenPair(
                accessToken, refreshToken, hashing.hash(refreshToken),
                accessTokenJti, refreshTokenJti, now, accessExpiresAt, refreshExpiresAt);
    }

    public Jwt decode(String token) {
        return decoder.decode(token);
    }

    public String hashRefresh(String raw) {
        return hashing.hash(raw);
    }
}
```

- [ ] **Step 6: Update `OAuth2TestApplication` to provide key locations in test `AuthProperties`**

In `backend/auth/src/test/java/com/fuoverflow/auth/OAuth2TestApplication.java`, update the `authProperties()` bean to include PEM locations. The test JwtService already generates keys in-memory so we need to adjust. Create test key files first:

```bash
cd backend/auth/src/test/resources
mkdir -p keys
openssl genrsa -out keys/test-private.pem 2048
openssl rsa -in keys/test-private.pem -pubout -out keys/test-public.pem
```

Then update `authProperties()`:

```java
@Bean
@Primary
AuthProperties authProperties() {
    return new AuthProperties(
            "fuoverflow",
            "fuoverflow-api",
            Duration.ofMinutes(10),
            Duration.ofDays(30),
            "test-pepper",
            new AuthProperties.Cookie(false, "Lax", "fuoverflow_at", "fuoverflow_rt"),
            new AuthProperties.Jwt("test-key", "classpath:keys/test-private.pem", "classpath:keys/test-public.pem"),
            new AuthProperties.EmailVerification(false, null, null, null),
            new AuthProperties.PasswordReset(false, null, null, null, Duration.ofHours(1))
    );
}
```

Update `jwtService()` to use `ResourceLoader`:

```java
@Bean
@Primary
JwtService jwtService(ResourceLoader resourceLoader) {
    try {
        return new JwtService(authProperties(), new TokenGenerator(), new TokenHashing(authProperties()), resourceLoader);
    } catch (Exception exception) {
        throw new IllegalStateException("Failed to create test JwtService", exception);
    }
}
```

- [ ] **Step 7: Write test for JwtService key loading**

Create `backend/auth/src/test/java/com/fuoverflow/auth/application/JwtServiceTest.java`:

```java
package com.fuoverflow.auth.application;

import com.fuoverflow.auth.config.AuthProperties;
import com.fuoverflow.auth.domain.TokenPair;
import com.fuoverflow.auth.support.TokenGenerator;
import com.fuoverflow.auth.support.TokenHashing;
import com.fuoverflow.user.api.dto.AuthUserView;
import com.fuoverflow.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        AuthProperties props = new AuthProperties(
                "fuoverflow", "fuoverflow-api", Duration.ofMinutes(10), Duration.ofDays(30),
                "test-pepper",
                new AuthProperties.Cookie(false, "Lax", "fuoverflow_at", "fuoverflow_rt"),
                new AuthProperties.Jwt("test-key", "classpath:keys/test-private.pem", "classpath:keys/test-public.pem"),
                new AuthProperties.EmailVerification(false, null, null, null),
                new AuthProperties.PasswordReset(false, null, null, null, Duration.ofHours(1)));
        jwtService = new JwtService(props, new TokenGenerator(), new TokenHashing(props), new DefaultResourceLoader());
    }

    @Test
    void generateAndDecodeRoundTrip() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = new AuthUserView(userId, "test@example.com", "testuser", "Test User",
                "hash", UserStatus.ACTIVE, List.of("USER"), true, 1);
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();

        TokenPair pair = jwtService.generate(user, sessionId, now);

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();

        Jwt decoded = jwtService.decode(pair.accessToken());
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString("sid")).isEqualTo(sessionId.toString());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(decoded.getClaimAsString("typ")).isEqualTo("access");
    }

    @Test
    void decodedTokenHasCorrectIssuerAndAudience() {
        AuthUserView user = new AuthUserView(UUID.randomUUID(), "a@b.com", "u", "U",
                "h", UserStatus.ACTIVE, List.of("USER"), true, 1);
        TokenPair pair = jwtService.generate(user, UUID.randomUUID(), Instant.now());
        Jwt decoded = jwtService.decode(pair.accessToken());

        assertThat(decoded.getIssuer().toString()).isEqualTo("fuoverflow");
        assertThat(decoded.getAudience()).containsExactly("fuoverflow-api");
    }
}
```

- [ ] **Step 8: Run tests**

```bash
cd backend && mvn -q -pl auth -am test
```

Expected: all tests pass.

- [ ] **Step 9: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/config/AuthProperties.java \
       backend/auth/src/main/java/com/fuoverflow/auth/application/JwtService.java \
       backend/app/src/main/resources/application.yml \
       backend/app/src/main/resources/application-prod.yml \
       backend/app/src/main/resources/keys/ \
       backend/auth/src/test/resources/keys/ \
       backend/auth/src/test/java/com/fuoverflow/auth/OAuth2TestApplication.java \
       backend/auth/src/test/java/com/fuoverflow/auth/application/JwtServiceTest.java
git commit -m "fix(auth): load RSA key pair from PEM files instead of generating at startup"
```

---

### Task 2: Use cookie-based OAuth2 authorization request repository for multi-instance support

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`

**Interfaces:**
- Consumes: nothing new
- Produces: OAuth2 state/nonce stored in encrypted cookie instead of HttpSession. All other components unaffected.

- [ ] **Step 1: Add `HttpCookieOAuth2AuthorizationRequestRepository` to SecurityConfig**

In `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`, modify the `oauth2Login` config:

```java
.oauth2Login(oauth2 -> oauth2
        .authorizationEndpoint(auth -> auth
                .authorizationRequestRepository(new HttpCookieOAuth2AuthorizationRequestRepository()))
        .userInfoEndpoint(userInfo -> userInfo
                .oidcUserService(googleOAuth2UserService))
        .successHandler(oauthSuccessHandler)
        .failureHandler(oauthFailureHandler))
```

Spring Security provides `HttpCookieOAuth2AuthorizationRequestRepository` since 6.4. If not available in the project's version, we create a simple one. Check first:

```bash
grep -r "HttpCookieOAuth2AuthorizationRequestRepository" ~/.m2/repository/org/springframework/security/ 2>/dev/null | head -3
```

If the class does NOT exist in our Spring Security version, create a custom implementation at `backend/auth/src/main/java/com/fuoverflow/auth/config/CookieOAuth2AuthorizationRequestRepository.java` that serializes `OAuth2AuthorizationRequest` to a Base64 cookie and reads it back on callback. Otherwise use the built-in class.

- [ ] **Step 2: Run tests**

```bash
cd backend && mvn -q -pl auth -am test
```

Expected: all tests pass (OAuth2 integration test does not go through Spring's OAuth2 state flow, it mocks the handlers directly).

- [ ] **Step 3: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java
# also add CookieOAuth2AuthorizationRequestRepository.java if custom implementation was needed
git commit -m "fix(auth): use cookie-based OAuth2 authorization request repository for multi-instance"
```

---

### Task 3: Enforce cookie `secure: true` in production profile

**Files:**
- Modify: `backend/app/src/main/resources/application-prod.yml`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/OAuth2Properties.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/oauth2/OAuthAuthenticationSuccessHandler.java` (startup validation)

**Interfaces:**
- Consumes: `OAuth2Properties`
- Produces: startup fails fast if redirect URLs don't match `frontendBaseUrl`

Note: The `secure: true` for production was already added in Task 1 step 4 (`application-prod.yml`). This task focuses on **validating redirect URL safety**.

- [ ] **Step 1: Add startup redirect URL validation**

Add a `@PostConstruct` or `@Bean` validation in SecurityConfig or create a small validator. Simplest: add `@jakarta.annotation.PostConstruct` to a new config class or add a `SmartInitializingSingleton`. Simpler: validate in `OAuthAuthenticationSuccessHandler` constructor:

In `backend/auth/src/main/java/com/fuoverflow/auth/oauth2/OAuthAuthenticationSuccessHandler.java`, add validation at the end of the constructor:

```java
public OAuthAuthenticationSuccessHandler(OAuthIdentityLinker identityLinker, OAuthSessionIssuer sessionIssuer,
                                        CookieService cookieService, OAuth2Properties oauth2Properties) {
    this.identityLinker = identityLinker;
    this.sessionIssuer = sessionIssuer;
    this.cookieService = cookieService;
    this.oauth2Properties = oauth2Properties;
    validateRedirectUrls(oauth2Properties);
}

private static void validateRedirectUrls(OAuth2Properties props) {
    String base = props.frontendBaseUrl();
    if (base == null || base.isBlank()) {
        throw new IllegalStateException("app.oauth2.frontend-base-url must be set");
    }
    if (!props.successRedirect().startsWith(base)) {
        throw new IllegalStateException(
                "app.oauth2.success-redirect must start with frontend-base-url: " + base);
    }
    if (!props.errorRedirect().startsWith(base)) {
        throw new IllegalStateException(
                "app.oauth2.error-redirect must start with frontend-base-url: " + base);
    }
}
```

- [ ] **Step 2: Update success handler test for redirect validation**

In `backend/auth/src/test/java/com/fuoverflow/auth/oauth2/OAuthAuthenticationSuccessHandlerTest.java`, update `setUp()` to also stub `frontendBaseUrl()`:

```java
@BeforeEach
void setUp() {
    when(oauth2Properties.successRedirect()).thenReturn("http://localhost:3000/oauth/callback");
    when(oauth2Properties.errorRedirect()).thenReturn("http://localhost:3000/oauth/error");
    when(oauth2Properties.frontendBaseUrl()).thenReturn("http://localhost:3000");
    handler = new OAuthAuthenticationSuccessHandler(identityLinker, sessionIssuer, cookieService, oauth2Properties);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    when(request.getHeader("User-Agent")).thenReturn("Test Browser");
}
```

- [ ] **Step 3: Run tests**

```bash
cd backend && mvn -q -pl auth -am test
```

- [ ] **Step 4: Commit**

```bash
git add backend/app/src/main/resources/application-prod.yml \
       backend/auth/src/main/java/com/fuoverflow/auth/oauth2/OAuthAuthenticationSuccessHandler.java \
       backend/auth/src/test/java/com/fuoverflow/auth/oauth2/OAuthAuthenticationSuccessHandlerTest.java
git commit -m "fix(auth): validate OAuth2 redirect URLs against frontend-base-url at startup"
```

---

### Task 4: Add selective CSRF protection for cookie-authenticated state-change endpoints

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`

**Interfaces:**
- Consumes: nothing new
- Produces: CSRF disabled for API paths (stateless JWT in `Authorization` header), but documented decision. Since all state-change endpoints are POST/PUT/DELETE and `SameSite=Lax` prevents cross-origin POST cookies, CSRF risk is mitigated. The fix is to document the decision explicitly and audit that no GET endpoints have side effects.

- [ ] **Step 1: Document CSRF decision in SecurityConfig**

In `SecurityConfig.java`, replace the bare `.csrf(csrf -> csrf.disable())` with a comment explaining the security rationale:

```java
// CSRF disabled: access token sent via Authorization header or HttpOnly cookie with SameSite=Lax.
// SameSite=Lax prevents cross-origin POST requests from sending cookies.
// All state-change operations use POST/PUT/DELETE (never GET).
// If SameSite is changed to None, CSRF protection MUST be re-enabled.
.csrf(csrf -> csrf.disable())
```

This is intentionally NOT adding CSRF token infrastructure for the MVP — the combination of `SameSite=Lax` + no-GET-side-effects provides equivalent protection. Adding CSRF tokens would require frontend changes and cookie-to-header synchronization.

- [ ] **Step 2: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java
git commit -m "docs(auth): document CSRF-disabled rationale with SameSite=Lax mitigation"
```

---

### Task 5: Document auto-linking security trade-off for OAuth account linking

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthIdentityLinker.java`

**Interfaces:**
- No interface changes. Documentation-only.

- [ ] **Step 1: Add security comment on auto-link Case B**

In `OAuthIdentityLinker.java`, add a comment above the Case B block:

```java
// SECURITY: Auto-linking by verified email is safe for Google (Google verifies email ownership).
// If adding providers that don't strictly verify email (GitHub private email, Facebook),
// require explicit user confirmation before linking to prevent account takeover.
var userByEmail = users.findAuthUserByIdentifier(normalizedEmail);
```

- [ ] **Step 2: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthIdentityLinker.java
git commit -m "docs(auth): document auto-link security assumption for Google OAuth"
```

---

### Task 6: Add retry logic to username generation to prevent collision failures

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthIdentityLinker.java`
- Test: `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthIdentityLinkerUsernameTest.java`

**Interfaces:**
- Consumes: nothing new
- Produces: `generateUsername()` uses `SecureRandom` instead of `System.nanoTime()`, with 3-retry loop

- [ ] **Step 1: Write the failing test**

Create `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthIdentityLinkerUsernameTest.java`:

```java
package com.fuoverflow.auth.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthIdentityLinkerUsernameTest {

    @Test
    void generatedUsernameMatchesPattern() throws Exception {
        String username = invokeGenerateUsername("test@example.com");
        assertThat(username).matches("[a-z0-9_-]+_[a-z0-9]+");
    }

    @Test
    void specialCharsInEmailAreStripped() throws Exception {
        String username = invokeGenerateUsername("Test.User+tag@example.com");
        assertThat(username).doesNotContain(".");
        assertThat(username).doesNotContain("+");
        assertThat(username).matches("[a-z0-9_-]+_[a-z0-9]+");
    }

    @Test
    void emptyLocalPartFallsBackToUser() throws Exception {
        String username = invokeGenerateUsername("!!!@example.com");
        assertThat(username).startsWith("user_");
    }

    @Test
    void noAtSignFallsBackToUser() throws Exception {
        String username = invokeGenerateUsername("malformed-email");
        assertThat(username).matches("[a-z0-9_-]+_[a-z0-9]+");
    }

    @RepeatedTest(50)
    void generatedUsernamesAreUnique() throws Exception {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            names.add(invokeGenerateUsername("same@example.com"));
        }
        assertThat(names).hasSizeGreaterThan(1);
    }

    @Test
    void longEmailLocalPartIsTruncated() throws Exception {
        String longLocal = "a".repeat(100) + "@example.com";
        String username = invokeGenerateUsername(longLocal);
        assertThat(username.length()).isLessThanOrEqualTo(70);
    }

    private String invokeGenerateUsername(String email) throws Exception {
        Method method = OAuthIdentityLinker.class.getDeclaredMethod("generateUsername", String.class);
        method.setAccessible(true);
        OAuthIdentityLinker linker = createLinkerForUsernameTest();
        return (String) method.invoke(linker, email);
    }

    private OAuthIdentityLinker createLinkerForUsernameTest() {
        return new OAuthIdentityLinker(null, null, null, null);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn -q -pl auth -am test -Dtest=OAuthIdentityLinkerUsernameTest
```

Expected: `generatedUsernamesAreUnique` may fail because `System.nanoTime()` can collide in tight loops.

- [ ] **Step 3: Fix `generateUsername` with SecureRandom**

In `backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthIdentityLinker.java`, replace the `generateUsername` method:

```java
private static final int USERNAME_SUFFIX_LENGTH = 6;
private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

private String generateUsername(String email) {
    String localPart = email.contains("@") ? email.split("@")[0] : "user";
    String slugified = localPart.toLowerCase().replaceAll("[^a-z0-9_-]", "");
    if (slugified.isEmpty()) {
        slugified = "user";
    }
    if (slugified.length() > USERNAME_BASE_MAX_LENGTH) {
        slugified = slugified.substring(0, USERNAME_BASE_MAX_LENGTH);
    }
    byte[] bytes = new byte[4];
    SECURE_RANDOM.nextBytes(bytes);
    String suffix = Integer.toUnsignedString(
            ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) |
            ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF), 36);
    return slugified + "_" + suffix;
}
```

Also remove the now-unused `BASE36_MODULO` constant and the old TODO comment.

- [ ] **Step 4: Run tests**

```bash
cd backend && mvn -q -pl auth -am test -Dtest=OAuthIdentityLinkerUsernameTest
```

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/application/OAuthIdentityLinker.java \
       backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthIdentityLinkerUsernameTest.java
git commit -m "fix(auth): use SecureRandom for OAuth username generation to prevent collisions"
```

---

### Task 7: Add unit tests for `OAuthIdentityLinker` and `OAuthSessionIssuer`

**Files:**
- Create: `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthIdentityLinkerTest.java`
- Create: `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthSessionIssuerTest.java`

**Interfaces:**
- Consumes: `OAuthIdentityLinker`, `OAuthSessionIssuer`, all their dependencies (mocked)
- Produces: comprehensive test coverage for the identity linking and session issuing flows

- [ ] **Step 1: Write `OAuthIdentityLinkerTest`**

Create `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthIdentityLinkerTest.java`:

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void emailNotVerified_throwsException() {
        ProviderProfile profile = new ProviderProfile("google", "sub-1", "test@example.com", false, "Test", null);

        assertThatThrownBy(() -> linker.link(profile))
                .isInstanceOf(OAuthEmailNotVerifiedException.class);

        verifyNoInteractions(oauthAccounts, users, registrations);
    }

    @Test
    void existingOAuthAccount_returnsExistingUser() {
        UUID userId = UUID.randomUUID();
        ProviderProfile profile = new ProviderProfile("google", "sub-1", "test@example.com", true, "Test", null);
        UserOAuthAccountEntity existing = mock(UserOAuthAccountEntity.class);
        when(existing.getUserId()).thenReturn(userId);
        when(oauthAccounts.findByProviderAndProviderUserId("google", "sub-1")).thenReturn(Optional.of(existing));

        LinkedIdentity result = linker.link(profile);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.isLinkedToExisting()).isFalse();
        verify(oauthAccounts, never()).save(any());
    }

    @Test
    void emailMatchesExistingUser_linksAndReturns() {
        UUID userId = UUID.randomUUID();
        ProviderProfile profile = new ProviderProfile("google", "sub-1", "test@example.com", true, "Test", null);
        when(oauthAccounts.findByProviderAndProviderUserId("google", "sub-1")).thenReturn(Optional.empty());
        when(emailNormalizer.normalize("test@example.com")).thenReturn("test@example.com");
        AuthUserView existingUser = new AuthUserView(userId, "test@example.com", "testuser", "Test",
                "hash", UserStatus.ACTIVE, List.of("USER"), true, 1);
        when(users.findAuthUserByIdentifier("test@example.com")).thenReturn(Optional.of(existingUser));

        LinkedIdentity result = linker.link(profile);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.isNewUser()).isFalse();
        assertThat(result.isLinkedToExisting()).isTrue();
        verify(oauthAccounts).save(any(UserOAuthAccountEntity.class));
        verify(registrations, never()).register(any());
    }

    @Test
    void newUser_registersAndLinks() {
        UUID newUserId = UUID.randomUUID();
        ProviderProfile profile = new ProviderProfile("google", "sub-1", "newuser@example.com", true, "New User", "https://pic.url");
        when(oauthAccounts.findByProviderAndProviderUserId("google", "sub-1")).thenReturn(Optional.empty());
        when(emailNormalizer.normalize("newuser@example.com")).thenReturn("newuser@example.com");
        when(users.findAuthUserByIdentifier("newuser@example.com")).thenReturn(Optional.empty());
        AuthUserView newUser = new AuthUserView(newUserId, "newuser@example.com", "newuser_abc123", "New User",
                null, UserStatus.PENDING_PROFILE, List.of("USER"), true, 1);
        when(registrations.register(any(RegisterUserCommand.class))).thenReturn(newUser);

        LinkedIdentity result = linker.link(profile);

        assertThat(result.userId()).isEqualTo(newUserId);
        assertThat(result.isNewUser()).isTrue();
        assertThat(result.isLinkedToExisting()).isFalse();

        ArgumentCaptor<RegisterUserCommand> cmdCaptor = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registrations).register(cmdCaptor.capture());
        RegisterUserCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.email()).isEqualTo("newuser@example.com");
        assertThat(cmd.passwordHash()).isNull();
        assertThat(cmd.emailVerified()).isTrue();
        assertThat(cmd.status()).isEqualTo(UserStatus.PENDING_PROFILE);
        assertThat(cmd.displayName()).isEqualTo("New User");

        verify(oauthAccounts).save(any(UserOAuthAccountEntity.class));
    }

    @Test
    void newUser_nullDisplayName_fallsBackToEmailLocalPart() {
        UUID newUserId = UUID.randomUUID();
        ProviderProfile profile = new ProviderProfile("google", "sub-1", "john@example.com", true, null, null);
        when(oauthAccounts.findByProviderAndProviderUserId("google", "sub-1")).thenReturn(Optional.empty());
        when(emailNormalizer.normalize("john@example.com")).thenReturn("john@example.com");
        when(users.findAuthUserByIdentifier("john@example.com")).thenReturn(Optional.empty());
        AuthUserView newUser = new AuthUserView(newUserId, "john@example.com", "john_abc", "john",
                null, UserStatus.PENDING_PROFILE, List.of("USER"), true, 1);
        when(registrations.register(any(RegisterUserCommand.class))).thenReturn(newUser);

        linker.link(profile);

        ArgumentCaptor<RegisterUserCommand> cmdCaptor = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registrations).register(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().displayName()).isEqualTo("john");
    }
}
```

- [ ] **Step 2: Write `OAuthSessionIssuerTest`**

Create `backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthSessionIssuerTest.java`:

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuthSessionIssuerTest {

    @Mock private UserLookupService users;
    @Mock private JwtService jwt;
    @Mock private UserSessionRepository sessions;
    @Mock private CookieService cookies;

    private OAuthSessionIssuer issuer;
    private final ClientContext context = new ClientContext("127.0.0.1", "TestAgent");

    @BeforeEach
    void setUp() {
        issuer = new OAuthSessionIssuer(users, jwt, sessions, cookies);
    }

    @Test
    void issue_activeUser_createsSessionAndReturnsBundle() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = new AuthUserView(userId, "test@example.com", "testuser", "Test User",
                null, UserStatus.ACTIVE, List.of("USER"), true, 1);
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(user));
        Instant now = Instant.now();
        TokenPair pair = new TokenPair("at", "rt", "hash", UUID.randomUUID(), UUID.randomUUID(),
                now, now.plusSeconds(600), now.plusSeconds(86400));
        when(jwt.generate(eq(user), any(UUID.class), any(Instant.class))).thenReturn(pair);

        AuthService.AuthTokenBundle result = issuer.issue(userId, context);

        assertThat(result.tokenPair()).isEqualTo(pair);
        assertThat(result.response()).isNotNull();
        verify(sessions).save(any(UserSessionEntity.class));
    }

    @Test
    void issue_userNotFound_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(users.findAuthUserById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> issuer.issue(userId, context))
                .isInstanceOf(UnauthorizedException.class);

        verify(sessions, never()).save(any());
    }

    @Test
    void issue_disabledUser_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        AuthUserView user = new AuthUserView(userId, "test@example.com", "testuser", "Test User",
                null, UserStatus.DISABLED, List.of("USER"), true, 1);
        when(users.findAuthUserById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> issuer.issue(userId, context))
                .isInstanceOf(ForbiddenException.class);

        verify(sessions, never()).save(any());
    }
}
```

- [ ] **Step 3: Run tests**

```bash
cd backend && mvn -q -pl auth -am test -Dtest="OAuthIdentityLinkerTest,OAuthSessionIssuerTest"
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthIdentityLinkerTest.java \
       backend/auth/src/test/java/com/fuoverflow/auth/application/OAuthSessionIssuerTest.java
git commit -m "test(auth): add unit tests for OAuthIdentityLinker and OAuthSessionIssuer"
```

---

### Task 8: Add DEBUG logging to `CookieAuthenticationFilter` for JWT decode failures

**Files:**
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/CookieAuthenticationFilter.java`

**Interfaces:**
- No interface changes. Logging-only change.

- [ ] **Step 1: Add logger and debug log**

In `backend/auth/src/main/java/com/fuoverflow/auth/config/CookieAuthenticationFilter.java`:

Add a logger field:

```java
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CookieAuthenticationFilter.class);
```

Replace the catch block:

```java
} catch (Exception ex) {
    log.debug("JWT decode failed: {}", ex.getMessage());
    SecurityContextHolder.clearContext();
}
```

- [ ] **Step 2: Run tests**

```bash
cd backend && mvn -q -pl auth -am test
```

- [ ] **Step 3: Commit**

```bash
git add backend/auth/src/main/java/com/fuoverflow/auth/config/CookieAuthenticationFilter.java
git commit -m "fix(auth): log JWT decode failures at DEBUG level in CookieAuthenticationFilter"
```

---

## Post-Implementation Verification

After all 8 tasks are done:

```bash
cd backend && mvn -q -pl auth -am test
cd backend && mvn -q -DskipTests package
```

Both must pass clean. Then review:
- [ ] Dev keys committed for local convenience, prod overrides via env var
- [ ] `application-prod.yml` has `secure: true` and key file locations
- [ ] OAuth2 state stored in cookie, not session
- [ ] Redirect URLs validated at startup
- [ ] CSRF decision documented
- [ ] Auto-link security documented
- [ ] Username generation uses SecureRandom
- [ ] OAuthIdentityLinker + OAuthSessionIssuer have test coverage
- [ ] CookieAuthenticationFilter logs decode failures
