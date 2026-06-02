# Kế hoạch chi tiết module Auth

Ngày tạo: 2026-06-02

## 1. Mục tiêu

Phát triển module `auth` cho backend Spring Boot monolith, phục vụ trước các chức năng:

- Đăng nhập bằng tài khoản + mật khẩu.
- Đăng ký.
- Introspect token: kiểm tra token AT/RT còn valid không và thời hạn token.
- Generate token: sinh Access Token và Refresh Token.

Thiết kế dựa trên:

- Spring Boot 3.5.x.
- Spring Security 6.5.x.
- Maven multi-module backend hiện có.
- PostgreSQL primary.
- Redis optional cache/token state.
- Không dùng database foreign key.
- Validate quan hệ trong code.

Tài liệu tham chiếu Context7:

- Spring Security 6.5: hỗ trợ Resource Server với JWT qua `oauth2ResourceServer().jwt(...)` và `JwtDecoder`.
- Spring Security 6.5: hỗ trợ bearer token dạng JWT hoặc opaque token/introspection.
- Spring Boot 3.5: dùng `@ConfigurationProperties`, Actuator health/metrics.

---

## 2. Quyết định token strategy

### 2.1 Khuyến nghị

Dùng mô hình:

```txt
Access Token: JWT, short-lived
Refresh Token: opaque random token, long-lived, rotating, only hash stored in DB
```

Lý do:

- Access Token JWT giúp API read path rẻ, không cần DB mỗi request.
- Refresh Token opaque dễ revoke, dễ rotate, an toàn hơn JWT refresh token.
- Phù hợp hệ thống low-cost 500–700 rps.

### 2.2 Access Token

Thông số đề xuất:

```txt
Type: JWT
Signing: RS256 preferred, HS256 acceptable for one-app MVP
TTL: 5–15 phút, khuyến nghị 10 phút
Storage browser: memory hoặc short-lived cookie tùy FE
```

Claims:

```json
{
  "iss": "fuoverflow",
  "aud": "fuoverflow-api",
  "sub": "<userId>",
  "preferred_username": "<username-or-email>",
  "roles": ["USER"],
  "sid": "<sessionId>",
  "jti": "<accessTokenJti>",
  "iat": 1710000000,
  "nbf": 1710000000,
  "exp": 1710000600,
  "typ": "access"
}
```

Không đưa vào JWT:

- Password hash.
- Email verification secret.
- Refresh token.
- PII không cần thiết.
- Mutable profile data nhiều.

### 2.3 Refresh Token

Thông số đề xuất:

```txt
Type: opaque random string
Entropy: >= 256-bit
TTL: 14–30 ngày
Storage DB: chỉ hash
Rotation: bật mặc định
Reuse detection: có
```

Raw refresh token chỉ trả cho client một lần. DB chỉ lưu:

```txt
refresh_token_hash = HMAC-SHA-256(rawToken, serverPepper)
```

Không bao giờ log raw RT.

---

## 3. Package structure module auth

```txt
backend/auth/src/main/java/com/fuoverflow/auth/
  api/
    AuthController.java
    dto/
      RegisterRequest.java
      LoginRequest.java
      RefreshTokenRequest.java
      TokenIntrospectionRequest.java
      TokenIntrospectionResponse.java
      AuthTokenResponse.java
      LogoutRequest.java
  application/
    AuthService.java
    TokenService.java
    SessionService.java
    PasswordService.java
    TokenIntrospectionService.java
  config/
    AuthProperties.java
    SecurityConfig.java
    JwtConfig.java
  domain/
    AuthenticatedUser.java
    TokenPair.java
    TokenType.java
    SessionStatus.java
  persistence/
    UserSessionEntity.java
    UserSessionRepository.java
  security/
    JwtAuthenticationConverterConfig.java
    JwtPrincipal.java
    CurrentUser.java
  support/
    TokenHashing.java
    TokenGenerator.java
```

---

## 4. Dependencies module auth

Trong `backend/auth/pom.xml`, thêm khi triển khai:

```xml
<dependency>
    <groupId>com.fuoverflow</groupId>
    <artifactId>fuoverflow-common</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>com.fuoverflow</groupId>
    <artifactId>fuoverflow-user</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Redis adapter có thể để optional hoặc tách sau.

---

## 5. Auth configuration

### 5.1 `AuthProperties`

```yaml
auth:
  issuer: fuoverflow
  audience: fuoverflow-api
  access-token-ttl: 10m
  refresh-token-ttl: 30d
  refresh-token-idle-ttl: 7d
  refresh-token-rotation-enabled: true
  max-sessions-per-user: 5
  clock-skew-seconds: 60
  refresh-token-hash-pepper: ${AUTH_REFRESH_TOKEN_PEPPER}
  jwt:
    algorithm: RS256
    key-id: local-dev-key-1
    private-key-location: ${AUTH_JWT_PRIVATE_KEY:classpath:keys/dev-private.pem}
    public-key-location: ${AUTH_JWT_PUBLIC_KEY:classpath:keys/dev-public.pem}
  redis:
    enabled: false
    prefix: fuoverflow:auth
```

### 5.2 Security config

Public endpoints:

```txt
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/token/refresh
```

Protected endpoints:

```txt
POST /api/v1/auth/logout
POST /api/v1/auth/logout-all
GET  /api/v1/users/me
PATCH /api/v1/users/me/profile
```

Internal-only endpoint:

```txt
POST /api/v1/auth/introspect
POST /api/v1/auth/token/generate
```

Security rules:

- Stateless API.
- Bearer JWT for protected APIs.
- `SessionCreationPolicy.STATELESS`.
- Disable CSRF only if FE stores tokens outside cookies and sends AT in `Authorization` header.
- If RT stored in HttpOnly cookie, enable CSRF for refresh/logout.

---

## 6. Database changes cho auth

Hiện schema đã có `user_sessions`, nhưng để support rotate/reuse đầy đủ nên nên bổ sung thêm migration riêng.

### 6.1 Đề xuất bảng `user_sessions` version mở rộng

```sql
create table user_sessions (
    id uuid primary key,
    user_id uuid not null,
    refresh_token_hash varchar(128) not null,
    refresh_token_family_id uuid not null,
    refresh_token_jti uuid not null,
    access_token_jti uuid null,
    issued_at timestamptz not null,
    access_expires_at timestamptz not null,
    refresh_expires_at timestamptz not null,
    last_used_at timestamptz null,
    revoked_at timestamptz null,
    revoked_reason varchar(64) null,
    replaced_by_session_id uuid null,
    ip_address varchar(64) null,
    user_agent text null,
    metadata_json jsonb not null default '{}',
    version bigint not null default 0,
    created_at timestamptz not null default now()
);

create unique index ux_user_sessions_refresh_hash on user_sessions(refresh_token_hash);
create unique index ux_user_sessions_refresh_jti on user_sessions(refresh_token_jti);
create index ix_user_sessions_user on user_sessions(user_id);
create index ix_user_sessions_family on user_sessions(refresh_token_family_id);
create index ix_user_sessions_refresh_expiry on user_sessions(refresh_expires_at);
create index ix_user_sessions_revoked on user_sessions(revoked_at);
create index ix_user_sessions_user_active on user_sessions(user_id, revoked_at, refresh_expires_at);
```

Nếu giữ bảng hiện tại trong `database-schema.sql`, cần migration alter:

- rename `token_hash` -> `refresh_token_hash` hoặc thêm column mới.
- thêm family/jti/expiry/revoked reason/version.

### 6.2 Không foreign key

Không tạo FK `user_sessions.user_id -> users.id`.

Bắt buộc validate trong code:

- Khi login: user phải tồn tại + active trước khi insert session.
- Khi refresh: session active + user active.
- Khi user disabled/deleted: revoke sessions.
- Scheduled orphan cleanup: session có `user_id` không còn user thì revoke/delete.

---

## 7. API design

### 7.1 Register

```http
POST /api/v1/auth/register
Content-Type: application/json
```

Request:

```json
{
  "email": "user@example.com",
  "username": "nguyenvana",
  "password": "StrongPassword123!",
  "displayName": "Nguyễn Văn A"
}
```

Response nếu không bắt buộc email verification:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque>",
  "tokenType": "Bearer",
  "accessTokenExpiresAt": "2026-06-02T10:10:00Z",
  "refreshTokenExpiresAt": "2026-07-02T10:00:00Z",
  "issuedAt": "2026-06-02T10:00:00Z",
  "sessionId": "uuid",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "username": "nguyenvana",
    "displayName": "Nguyễn Văn A",
    "status": "ACTIVE"
  }
}
```

Response nếu bắt buộc email verification:

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "username": "nguyenvana",
  "displayName": "Nguyễn Văn A",
  "status": "PENDING_EMAIL_VERIFICATION",
  "emailVerified": false
}
```

Khuyến nghị giai đoạn đầu: chưa cần email verification, auto-login sau register.

Validation:

- email valid, max 320.
- username 3–64, normalized unique.
- password policy.
- displayName max 120.

Errors:

- `400` invalid input.
- `409 EMAIL_TAKEN`.
- `409 USERNAME_TAKEN`.
- `400 WEAK_PASSWORD`.

---

### 7.2 Login

```http
POST /api/v1/auth/login
Content-Type: application/json
```

Request:

```json
{
  "identifier": "user@example.com",
  "password": "StrongPassword123!"
}
```

`identifier` có thể là email hoặc username.

Response:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque>",
  "tokenType": "Bearer",
  "accessTokenExpiresAt": "2026-06-02T10:10:00Z",
  "refreshTokenExpiresAt": "2026-07-02T10:00:00Z",
  "issuedAt": "2026-06-02T10:00:00Z",
  "sessionId": "uuid",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "username": "nguyenvana",
    "displayName": "Nguyễn Văn A",
    "status": "ACTIVE"
  }
}
```

Rules:

- Login failure trả generic `INVALID_CREDENTIALS`.
- Không nói email/username có tồn tại hay không.
- Rate limit theo identifier + IP.
- Audit success/failure.

---

### 7.3 Refresh / generate new AT + RT

```http
POST /api/v1/auth/token/refresh
Content-Type: application/json
```

Request:

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

Response:

```json
{
  "accessToken": "<new-jwt>",
  "refreshToken": "<new-opaque-refresh-token>",
  "tokenType": "Bearer",
  "accessTokenExpiresAt": "2026-06-02T10:20:00Z",
  "refreshTokenExpiresAt": "2026-07-02T10:10:00Z",
  "issuedAt": "2026-06-02T10:10:00Z",
  "sessionId": "uuid",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "username": "nguyenvana",
    "displayName": "Nguyễn Văn A",
    "status": "ACTIVE"
  }
}
```

Flow:

1. Hash input refresh token.
2. Find session by hash.
3. Check not revoked.
4. Check not expired.
5. Check user active.
6. Check password not changed after session issued.
7. Generate new AT + new RT.
8. Revoke/rotate old RT.
9. Return new pair.

Reuse detection:

- Nếu RT đã rotated/revoked mà được dùng lại:
  - revoke whole `refresh_token_family_id`.
  - return `401 REFRESH_REUSE_DETECTED`.

---

### 7.4 Introspect token

```http
POST /api/v1/auth/introspect
Content-Type: application/json
Authorization: Bearer <admin-or-internal-token>
```

Request:

```json
{
  "token": "<access-or-refresh-token>",
  "tokenType": "ACCESS"
}
```

`tokenType`:

```txt
ACCESS
REFRESH
```

Response active:

```json
{
  "active": true,
  "tokenType": "ACCESS",
  "subject": "userId",
  "username": "nguyenvana",
  "sessionId": "uuid",
  "jti": "uuid",
  "issuedAt": "2026-06-02T10:00:00Z",
  "notBefore": "2026-06-02T10:00:00Z",
  "expiresAt": "2026-06-02T10:10:00Z",
  "revokedAt": null,
  "revokedReason": null,
  "roles": ["USER"],
  "reason": null
}
```

Response inactive:

```json
{
  "active": false,
  "tokenType": "ACCESS",
  "reason": "TOKEN_EXPIRED",
  "expiresAt": "2026-06-02T10:10:00Z"
}
```

Important:

- Không public cho browser.
- Chỉ dùng nội bộ/admin.
- Không trả token raw/token hash.
- Với ACCESS JWT: verify signature + date + issuer + type.
- Với REFRESH: hash token rồi lookup DB.

---

### 7.5 Internal generate token

Không khuyến nghị expose public.

Preferred: service method.

```java
TokenPair generateTokenPair(AuthenticatedUser user, SessionContext context);
```

Nếu cần API nội bộ:

```http
POST /api/v1/auth/token/generate
Authorization: Bearer <internal-service-token>
```

Request không được cho client tự set roles/expiry tùy ý.

```json
{
  "userId": "uuid",
  "reason": "LOGIN"
}
```

Server tự load user + roles + status từ DB rồi generate.

---

## 8. Service design

### 8.1 `AuthService`

Methods:

```java
AuthTokenResponse register(RegisterRequest request, ClientContext context);
AuthTokenResponse login(LoginRequest request, ClientContext context);
AuthTokenResponse refresh(RefreshTokenRequest request, ClientContext context);
void logout(LogoutRequest request, UUID currentUserId);
void logoutAll(UUID currentUserId);
TokenIntrospectionResponse introspect(TokenIntrospectionRequest request);
```

### 8.2 `TokenService`

Methods:

```java
TokenPair generateTokenPair(AuthUserView user, UUID sessionId, Instant now);
JwtClaims parseAndValidateAccessToken(String token);
String generateOpaqueRefreshToken();
String hashRefreshToken(String rawToken);
```

### 8.3 `SessionService`

Methods:

```java
UserSession createSession(AuthUserView user, TokenPair tokenPair, ClientContext context);
UserSession rotateRefreshToken(String rawRefreshToken, ClientContext context);
void revokeSession(UUID sessionId, String reason);
void revokeAllByUserId(UUID userId, String reason);
void revokeFamily(UUID familyId, String reason);
SessionIntrospection introspectRefreshToken(String rawRefreshToken);
```

### 8.4 `PasswordService`

Methods:

```java
String encode(String rawPassword);
boolean matches(String rawPassword, String encodedPassword);
void validatePolicy(String rawPassword);
```

---

## 9. Redis usage optional

Redis không là source of truth.

Keys:

```txt
auth:sid:{sessionId} -> active session summary
auth:revoked-at:{jti} -> revoked access token marker
auth:rt:{hash} -> optional refresh token lookup cache
```

TTL:

- `sid`: tới refresh token expiry.
- `revoked-at`: tới AT expiry.
- `rt`: short TTL hoặc tới RT expiry.

Nếu Redis down:

- fallback DB.
- log warning.
- metric alert.

---

## 10. Error contract

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid username or password",
  "traceId": "...",
  "timestamp": "2026-06-02T10:00:00Z"
}
```

Codes:

```txt
INVALID_CREDENTIALS
EMAIL_TAKEN
USERNAME_TAKEN
WEAK_PASSWORD
USER_DISABLED
EMAIL_NOT_VERIFIED
TOKEN_INVALID
TOKEN_EXPIRED
SESSION_REVOKED
REFRESH_REUSE_DETECTED
TOO_MANY_SESSIONS
RATE_LIMITED
```

---

## 11. Security checklist

- AT TTL ngắn, 5–15 phút.
- RT opaque, random mạnh, chỉ lưu hash.
- Không log token.
- Rate limit login/register/refresh.
- Generic login error.
- Revoke sessions khi password/user state đổi.
- Key rotation có `kid`.
- CSRF nếu token dùng cookie.
- Không public token generate endpoint.
- Introspect endpoint internal/admin only.
- Audit login success/failure/refresh/logout/reuse detection.

---

## 12. Implementation phases

### Phase A — Foundation

1. Update Maven dependencies for `auth`, `user`, `app`.
2. Add `AuthProperties`.
3. Add shared error response in `common`.
4. Add Flyway migration for auth/session fields.

### Phase B — User integration

1. Build `AuthUserView` from user module.
2. Build `UserLookupService` port.
3. Build `UserRegistrationService` port.

### Phase C — Password auth

1. Add `PasswordService`.
2. Add password encoder bean.
3. Add login validation.
4. Add generic auth errors.

### Phase D — Token

1. Add JWT key loading.
2. Add JWT generation.
3. Add JWT decoder.
4. Add opaque RT generator/hash.
5. Add session persistence.

### Phase E — APIs

1. `POST /api/v1/auth/register`.
2. `POST /api/v1/auth/login`.
3. `POST /api/v1/auth/token/refresh`.
4. `POST /api/v1/auth/introspect` internal/admin.
5. Optional `POST /api/v1/auth/token/generate` internal-only.

### Phase F — Hardening

1. Rate limit.
2. Audit events.
3. Refresh reuse detection.
4. Logout/logout-all.
5. Redis token state cache optional.

---

## 13. Test plan

Unit tests:

- Password policy.
- Password match.
- Refresh token hash deterministic.
- JWT claims/expiry.
- Introspection active/expired/invalid.
- Refresh rotation.
- Reuse detection.

Integration tests:

- Register success.
- Register duplicate email/username.
- Login success.
- Login wrong password.
- Refresh success.
- Old RT reuse revokes family.
- Introspect AT active/expired.
- Introspect RT active/revoked.
- Disabled user cannot login/refresh.

Security tests:

- Protected endpoint rejects missing/invalid AT.
- Public endpoints accessible.
- Internal introspect/generate blocked for normal user.

---

## 14. Rủi ro chính

| Rủi ro | Giảm thiểu |
|---|---|
| Public generate token bị lạm dụng | Không expose public, internal-only |
| Raw RT bị lộ | Chỉ lưu hash, redact logs |
| JWT AT không revoke tức thì | TTL ngắn, check DB/Redis với endpoint nhạy cảm |
| Refresh rotation race | Transaction + optimistic lock/row lock |
| No-FK tạo orphan sessions | Revoke on user state change + cleanup job |
| CSRF nếu dùng cookie | SameSite + HttpOnly + Secure + CSRF token |
| Brute force login | Rate limit + audit + throttle |
| Key rotation lỗi | JWT header `kid`, giữ public key cũ tới khi AT hết hạn |
