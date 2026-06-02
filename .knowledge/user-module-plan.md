# Kế hoạch chi tiết module User

Ngày tạo: 2026-06-02

## 1. Mục tiêu

Module `user` quản lý dữ liệu người dùng và cung cấp các port/service cho module `auth`.

Giai đoạn đầu phục vụ các chức năng:

- Đăng ký user.
- Login lookup cho auth.
- Trả user profile cơ bản sau login/register.
- Hỗ trợ introspect token trả thông tin user tối thiểu.
- Hỗ trợ generate token bằng cách cung cấp user state/roles.

Không làm trong giai đoạn đầu:

- Social graph.
- Preferences phức tạp.
- Follow/friend.
- Address/profile mở rộng.
- Permission/role phức tạp.
- Membership/business behavior.
- Password reset/email verification đầy đủ nếu chưa cần.

---

## 2. Ranh giới module

`user` chỉ expose facts và state transition của user. `auth` quyết định authentication/token/session.

### 2.1 User module chịu trách nhiệm

- User entity.
- User persistence.
- Email/username uniqueness.
- User status.
- Basic profile.
- Register user.
- Lookup user for auth.
- Update last login timestamp.
- Mark email verified nếu sau này cần.
- Disable/delete user state.

### 2.2 Auth module chịu trách nhiệm

- Password policy.
- Password check.
- Login decision.
- Token generation.
- Refresh token/session.
- Introspection.
- Logout/revoke.
- Rate limit/audit auth.

### 2.3 Dependency rule

```txt
auth -> user -> common
auth không import user.persistence trực tiếp
```

Auth chỉ gọi user qua service/port.

---

## 3. Package structure module user

```txt
backend/user/src/main/java/com/fuoverflow/user/
  api/
    UserProfileController.java
    dto/
      UserProfileResponse.java
      UpdateUserProfileRequest.java
      RegisteredUserResponse.java
      AuthUserView.java
  application/
    UserRegistrationService.java
    UserLookupService.java
    UserProfileService.java
    UserEmailVerificationService.java
    UserStatusService.java
  domain/
    User.java
    UserStatus.java
    UserRole.java
    UserRegisteredEvent.java
  persistence/
    UserEntity.java
    UserRepository.java
    UserMapper.java
  validation/
    UsernameNormalizer.java
    EmailNormalizer.java
```

Có thể giản lược entity/domain nếu dùng JPA entity trực tiếp ở MVP, nhưng vẫn giữ package boundary.

---

## 4. User domain model

### 4.1 Fields

```txt
id: UUID
email: String
normalizedEmail: String
username: String
usernameNormalized: String
passwordHash: String
status: UserStatus
roles: List<String> hoặc JSONB
emailVerifiedAt: Instant?
displayName: String?
firstName: String?
lastName: String?
avatarUrl: String?
createdAt: Instant
updatedAt: Instant
lastLoginAt: Instant?
passwordChangedAt: Instant?
version: Long
```

### 4.2 Status enum

```java
public enum UserStatus {
    PENDING_EMAIL_VERIFICATION,
    ACTIVE,
    DISABLED,
    DELETED
}
```

Giai đoạn đầu nếu chưa email verification:

```txt
Register -> ACTIVE
```

Nếu bật email verification sau:

```txt
Register -> PENDING_EMAIL_VERIFICATION
Confirm email -> ACTIVE
```

### 4.3 Role đơn giản ban đầu

Ban đầu dùng role đơn giản:

```txt
USER
MODERATOR
ADMIN
```

Có thể lưu trong `roles_json` hoặc text array/string. Vì chưa làm permission phức tạp, chưa cần bảng role riêng trong giai đoạn auth/user.

---

## 5. Database plan

Hiện `.knowledge/database-schema.sql` đã có bảng `users`, nhưng để phục vụ auth tốt hơn nên cần bổ sung hoặc điều chỉnh.

### 5.1 Đề xuất shape bảng `users`

```sql
create table users (
    id uuid primary key,
    email varchar(320) not null,
    normalized_email varchar(320) not null,
    username varchar(64) not null,
    username_normalized varchar(64) not null,
    password_hash varchar(255) not null,
    display_name varchar(120) not null,
    first_name varchar(80) null,
    last_name varchar(80) null,
    avatar_url text null,
    status varchar(40) not null default 'ACTIVE',
    roles_json jsonb not null default '["USER"]'::jsonb,
    email_verified_at timestamptz null,
    last_login_at timestamptz null,
    password_changed_at timestamptz null,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint users_status_check check (status in ('PENDING_EMAIL_VERIFICATION','ACTIVE','DISABLED','DELETED'))
);

create unique index ux_users_normalized_email_live on users(normalized_email) where deleted_at is null;
create unique index ux_users_username_normalized_live on users(username_normalized) where deleted_at is null;
create index ix_users_status on users(status);
create index ix_users_created on users(created_at desc);
```

### 5.2 Notes so với schema hiện tại

Schema hiện tại có:

```txt
username
email
password_hash
display_name
status lowercase active/pending/banned/deleted
```

Khi implement nên chọn 1 trong 2 hướng:

#### Option A — giữ schema hiện tại

- Dùng `lower(username)` và `lower(email)` unique index hiện có.
- Map status hiện có: `active`, `pending`, `banned`, `deleted`.
- Ít migration hơn.

#### Option B — migrate sang normalized columns

- Thêm `normalized_email`, `username_normalized`.
- Status uppercase rõ hơn trong Java enum.
- Dễ query/auth hơn.

Khuyến nghị: Option B nếu chưa có dữ liệu production.

---

## 6. Validation rules

### 6.1 Email

Rules:

- Required.
- `@Email`.
- Max 320 chars.
- Normalize bằng:

```java
trim().toLowerCase(Locale.ROOT)
```

Không làm Gmail dot stripping nếu product chưa yêu cầu.

### 6.2 Username

Rules:

```txt
Length: 3–64
Allowed: a-z, A-Z, 0-9, underscore, dot, dash
Normalized: lower-case
Reserved words: admin, root, system, api, auth, login, register, me
```

Regex đề xuất:

```txt
^[a-zA-Z0-9._-]{3,64}$
```

### 6.3 Password

Password policy nằm ở auth module, nhưng register command có thể nhận password.

Giai đoạn đầu:

```txt
min length 8 hoặc 12
must not be blank
max length 128
```

Auth module hash trước khi gọi user service hoặc gọi shared `PasswordService`.

### 6.4 Profile fields

```txt
displayName: required, max 120
firstName: optional, max 80
lastName: optional, max 80
avatarUrl: optional, max 500, URL valid nếu có
```

Trim string. Blank optional -> null.

---

## 7. Application services / ports

### 7.1 `UserRegistrationService`

```java
RegisteredUser register(RegisterUserCommand command);
```

Command:

```java
public record RegisterUserCommand(
    String email,
    String username,
    String passwordHash,
    String displayName,
    String firstName,
    String lastName,
    String avatarUrl
) {}
```

Flow:

1. Normalize email/username.
2. Validate fields.
3. Check duplicate for friendly error.
4. Insert user.
5. Catch DB unique violation -> duplicate error.
6. Return `RegisteredUser`.

Do not issue token here. Auth module issues token.

---

### 7.2 `UserLookupService`

```java
Optional<AuthUserView> findAuthUserByIdentifier(String identifier);
Optional<AuthUserView> findAuthUserById(UUID userId);
```

`identifier` có thể email hoặc username.

`AuthUserView`:

```java
public record AuthUserView(
    UUID id,
    String email,
    String username,
    String passwordHash,
    String displayName,
    UserStatus status,
    List<String> roles,
    Instant emailVerifiedAt,
    Instant passwordChangedAt,
    Instant deletedAt
) {}
```

Auth dùng view này để:

- Check password.
- Check active state.
- Build token claims.
- Check refresh token still valid after password change.

---

### 7.3 `UserProfileService`

```java
UserProfileResponse getCurrentUser(UUID userId);
UserProfileResponse updateProfile(UUID userId, UpdateUserProfileCommand command);
```

Only current user for now.

No admin API yet.

---

### 7.4 `UserStatusService`

```java
void disableUser(UUID userId, String reason);
void markDeleted(UUID userId, String reason);
void markLastLogin(UUID userId, Instant at);
```

When user disabled/deleted:

- emit event or call auth session revocation.
- auth revokes sessions.

---

### 7.5 `UserEmailVerificationService`

Future-ready:

```java
void markEmailVerified(UUID userId, Instant verifiedAt);
```

Auth/email verification module validates token; user module only updates user state.

---

## 8. API design user module

### 8.1 Get current user

```http
GET /api/v1/users/me
Authorization: Bearer <AT>
```

Response:

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "username": "nguyenvana",
  "displayName": "Nguyễn Văn A",
  "firstName": "Văn A",
  "lastName": "Nguyễn",
  "avatarUrl": "/uploads/avatars/2026/06/02/uuid.png",
  "status": "ACTIVE",
  "roles": ["USER"],
  "emailVerified": true,
  "createdAt": "2026-06-02T10:00:00Z"
}
```

Never return:

- `passwordHash`.
- `normalizedEmail`.
- `usernameNormalized`.
- `version`.
- internal audit metadata.

---

### 8.2 Update current user profile

```http
PATCH /api/v1/users/me/profile
Authorization: Bearer <AT>
Content-Type: application/json
```

Request:

```json
{
  "displayName": "Nguyễn Văn A",
  "firstName": "Văn A",
  "lastName": "Nguyễn",
  "avatarUrl": "/uploads/avatars/2026/06/02/uuid.png"
}
```

Response: same as `GET /api/v1/users/me`.

Rules:

- user id lấy từ principal, không lấy từ body.
- only profile fields.
- không update email/username/password trong API này.

---

## 9. Repository design

```java
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByNormalizedEmailAndDeletedAtIsNull(String normalizedEmail);
    Optional<UserEntity> findByUsernameNormalizedAndDeletedAtIsNull(String usernameNormalized);
    boolean existsByNormalizedEmailAndDeletedAtIsNull(String normalizedEmail);
    boolean existsByUsernameNormalizedAndDeletedAtIsNull(String usernameNormalized);
}
```

Nếu giữ schema hiện tại chưa có normalized columns:

```java
@Query("select u from UserEntity u where lower(u.email) = :email and u.deletedAt is null")
Optional<UserEntity> findByEmailNormalized(@Param("email") String normalizedEmail);
```

---

## 10. Events

User module có thể emit events nội bộ:

```txt
UserRegisteredEvent
UserEmailVerifiedEvent
UserDisabledEvent
UserDeletedEvent
UserPasswordChangedEvent
UserProfileUpdatedEvent
```

Giai đoạn đầu có thể chỉ dùng method call. Sau này đưa vào outbox.

Events liên quan auth:

- `UserDisabledEvent` -> auth revoke sessions.
- `UserDeletedEvent` -> auth revoke sessions.
- `UserPasswordChangedEvent` -> auth revoke sessions hoặc reject refresh by `passwordChangedAt`.

---

## 11. Error contract

Use common error response:

```json
{
  "code": "USERNAME_TAKEN",
  "message": "Username is already used",
  "traceId": "...",
  "timestamp": "2026-06-02T10:00:00Z"
}
```

Codes:

```txt
EMAIL_TAKEN
USERNAME_TAKEN
USER_NOT_FOUND
USER_DISABLED
USER_DELETED
INVALID_USERNAME
INVALID_PROFILE
```

Mappings:

| Case | HTTP |
|---|---:|
| Invalid DTO | 400 |
| Duplicate email/username | 409 |
| Current user not found | 404 or 401 depending auth state |
| Disabled/deleted user | 403 |

---

## 12. Implementation phases

### Phase A — Schema alignment

1. Decide schema option A or B.
2. If Option B, add migration for normalized columns/status/roles.
3. Update `UserEntity` accordingly.

### Phase B — Domain + persistence

1. Add `UserStatus` enum.
2. Add `UserEntity`.
3. Add `UserRepository`.
4. Add mapper between entity and DTO/view.

### Phase C — Validation

1. Add `EmailNormalizer`.
2. Add `UsernameNormalizer`.
3. Add reserved username list.
4. Add profile sanitizer.

### Phase D — Application services

1. Implement `UserRegistrationService`.
2. Implement `UserLookupService`.
3. Implement `UserProfileService`.
4. Add `UserStatusService`.

### Phase E — APIs

1. Add `GET /api/v1/users/me`.
2. Add `PATCH /api/v1/users/me/profile`.
3. Register endpoint remains in auth module but calls user registration service.

### Phase F — Integration with auth

1. Auth login uses `UserLookupService`.
2. Auth register calls `UserRegistrationService`.
3. Auth token generation uses `AuthUserView`.
4. Auth refresh checks `passwordChangedAt` and `status`.

---

## 13. Test plan

Unit tests:

- Normalize email.
- Normalize username.
- Reject invalid username.
- Reject reserved username.
- Profile trim/blank-to-null.
- Mapper hides password hash.

Repository tests:

- Find by normalized email.
- Find by normalized username.
- Unique email violation.
- Unique username violation.
- Soft deleted user ignored.

Service tests:

- Register success.
- Register duplicate email.
- Register duplicate username.
- Lookup by email.
- Lookup by username.
- Disabled user returned with status for auth decision.
- Update profile success.

API tests:

- `GET /api/v1/users/me` returns current profile.
- `PATCH /api/v1/users/me/profile` updates allowed fields.
- Profile response never returns password hash.

Auth integration tests:

- Login uses user lookup.
- Disabled user cannot login.
- Refresh rejected if user disabled/deleted.
- Refresh rejected if password changed after session issue.

---

## 14. Rủi ro chính

| Rủi ro | Giảm thiểu |
|---|---|
| Duplicate race khi register | DB unique constraint + catch exception |
| Case-sensitive duplicate email | normalized email + unique index |
| Auth import repository user trực tiếp | expose service/port only |
| Leak password hash qua DTO | mapper/read model riêng |
| Hard delete phá session/audit | dùng DISABLED/DELETED soft state |
| No-FK orphan session | auth refresh luôn check user active |
| User module phình to | không thêm business profile/phân quyền phức tạp lúc này |
| Email verification state không rõ | chốt policy: active ngay hoặc pending đến khi verify |

---

## 15. Quyết định còn cần chốt trước khi code

1. Có dùng username bắt buộc không, hay chỉ email + password?
2. Sau đăng ký có auto-login không?
3. Có bắt buộc email verification trước login không?
4. Token lưu phía FE bằng header bearer hay HttpOnly cookie?
5. Dùng RS256 key pair hay HS256 secret cho MVP?

Khuyến nghị mặc định nếu chưa chốt:

```txt
username bắt buộc: yes
email bắt buộc: yes
auto-login sau register: yes
email verification phase sau: no ở MVP
FE gửi AT bằng Authorization header
RT gửi trong body refresh request ở MVP; nâng lên HttpOnly cookie sau
JWT signing: HS256 cho local MVP, RS256 trước production
```
