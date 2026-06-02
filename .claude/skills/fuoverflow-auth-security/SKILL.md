---
name: fuoverflow-auth-security
description: Use for auth, JWT, refresh token, Spring Security, sessions, password, OAuth login, authorization, and security review tasks.
---

# FuOverflow Auth & Security Skill

Use this skill for authentication, authorization, token storage, Spring Security config, account lifecycle, login/register, logout, refresh, token introspection, and security reviews.

## Source of truth

Read `.knowledge/auth-module-plan.md` before implementing auth.

## Recommended auth model

For MVP web app:

```text
Access Token: JWT, short-lived, preferably RS256
Refresh Token: opaque random token, rotating, hash stored server-side only
Browser storage: HttpOnly Secure SameSite cookie preferred
```

Do not store raw refresh tokens in DB. Store only a hash, preferably HMAC-SHA-256 with server-side pepper or equivalent.

## JWT rules

Access token claims may include:

- `iss`: `fuoverflow`
- `aud`: `fuoverflow-api`
- `sub`: user UUID
- `preferred_username`: username/email when needed
- `roles`: role list
- `sid`: session ID
- `jti`: access token ID
- `iat`, `nbf`, `exp`
- `typ`: `access`

Do not include:

- password hash
- refresh token
- email verification secret
- PII not needed for authorization
- mutable profile data that would become stale

## Refresh token rules

- Generate with at least 256 bits of entropy.
- Return raw refresh token only once.
- Store hash only.
- Rotate by default.
- Detect reuse: if an already-rotated/revoked token is used, revoke the whole session chain if implemented.
- Never log raw RT.

## Browser cookie rules

When using cookies:

```text
HttpOnly
Secure
SameSite=Lax or Strict
Path limited when useful
Short max-age for access cookie if used
```

If cross-site cookies are required with `SameSite=None`, add CSRF protection.

## Password rules

- Use Spring Security `PasswordEncoder`.
- Use BCrypt/Argon2 style adaptive hashing.
- Never write custom password hashing.
- Do not log login password or password hash.
- Normalize email/username consistently before lookup.

## Spring Security rules

- Public endpoints should be explicit.
- Protected endpoints should require authentication by default.
- Method-level authorization is acceptable for sensitive business operations.
- CORS must be explicit and environment-based.
- Keep CSRF enabled or intentionally configured based on cookie/header auth strategy.
- Do not disable security globally just to make tests pass.

Suggested public endpoints:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/introspect
GET  /actuator/health
```

Logout should revoke/expire refresh token/session.

## Authorization rules

- Use role/permission claims for coarse access.
- For object-level permissions, check ownership/moderator/admin status in service layer.
- Do not trust client-supplied `userId` for acting user; derive from authenticated principal.

## Security review checklist

Before finishing auth/security work, verify:

- Raw tokens are never stored or logged.
- Access token expiry is short.
- Refresh token rotation exists or is explicitly deferred.
- Failed login behavior does not leak whether email exists.
- Errors are generic for auth failures.
- CORS and cookie SameSite are compatible.
- Tests cover at least success and major failure paths.
