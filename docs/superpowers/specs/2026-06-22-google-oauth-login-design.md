# Google OAuth Login — Design

Date: 2026-06-22
Status: Approved (pending user spec-review)
Owner: backend
Related: `.knowledge/auth-module-plan.md`, `.knowledge/user-module-plan.md`, `database-schema.sql` (users, user_oauth_accounts)

## 1. Goals & Non-Goals

### In scope

- Login + register via Google account using OAuth2 Authorization Code flow (server-side redirect).
- Auto-link existing local user when Google `email_verified=true` and `email` matches a local user.
- Auto-create a new local user when Google account is not yet known (auto-generated fillable username / display name placeholder).
- Issue the same cookie-based session used by password login (`fuoverflow_at`, `fuoverflow_rt`).
- Inherit refresh-token rotation, reuse-detection, and audit rules from existing `SessionService`.

### Out of scope

- Other providers (Facebook, GitHub, Apple, …).
- Account-unlink endpoint (future).
- Email verification flow for OAuth users — Google `email_verified=true` is the contract.
- FE integration work — only BE deliverable this round; FE endpoints and redirect targets are documented for future wiring.

## 2. Approach Summary

Use Spring Security 6 `oauth2Login()` with a custom `OAuth2UserService<OidcUserRequest, OidcUser>` and a custom `AuthenticationSuccessHandler`. Spring's filter chain handles:

- Redirect to Google authorize endpoint.
- Code → token exchange (uses `client_secret` server-side, never exposed).
- `id_token` signature + audience verification via Google JWKS.
- CSRF `state` generation and verification.

Application code handles the business side:

- `OAuthIdentityLinker`: maps `(provider, provider_user_id, email)` → existing or new local `user_id`.
- `OAuthSessionIssuer`: reuses `JwtService` + `SessionService` to issue AT/RT, then `CookieService` writes cookies.
- `OAuthAuthenticationSuccessHandler`: orchestrates the above, then 302-redirects to FE with `provider=google&new=<bool>`.
- `OAuthAuthenticationFailureHandler`: 302-redirects to FE error page with a stable code.

`JdbcOAuth2AuthorizedClientService` is **not** required for MVP — the `OAuth2AuthorizedClient` is consumed inline during the success handler and then discarded; the long-lived session state lives in `user_sessions` exactly as the password flow does.

## 3. Module Structure

```text
backend/auth/src/main/java/com/fuoverflow/auth/
  application/
    OAuthIdentityLinker.java
    OAuthSessionIssuer.java
  config/
    OAuth2Properties.java
    SecurityConfig.java         # add oauth2Login() chain
  oauth2/
    GoogleOAuth2UserService.java
    PrincipalOAuth2User.java
    OAuthAuthenticationSuccessHandler.java
    OAuthAuthenticationFailureHandler.java
```

`pom.xml` adds one dependency: `spring-boot-starter-oauth2-client`.

Package `com.fuoverflow.auth.oauth2` is picked up by the existing `AuthModule` component scan (no extra config needed).

## 4. Flow

### F1 — Login kick-off (FE → BE)

FE renders `<a href="/oauth2/authorization/google">Login with Google</a>`. Spring `OAuth2AuthorizationRequestRedirectFilter` issues a 302 to:

```
https://accounts.google.com/o/oauth2/v2/auth
  ?client_id=…
  &redirect_uri={baseUrl}/login/oauth2/code/google
  &response_type=code
  &scope=openid%20email%20profile
  &state=<csrf-token>
```

### F2 — Callback (Google → BE)

```
GET /login/oauth2/code/google?code=…&state=…
```

`OAuth2LoginAuthenticationFilter` does the following (via `OidcAuthorizationCodeAuthenticationProvider`):

1. Exchanges `code` for tokens using `client_secret` (server-side).
2. Fetches Google JWKS and verifies `id_token` signature, issuer, audience, expiry.
3. Calls `GoogleOAuth2UserService.loadUser(request)` (our subclass of `OidcUserService`):
   - Returns the verified `OidcUser` containing `sub`, `email`, `email_verified`, `name`, `picture`.
   - Wraps it in `PrincipalOAuth2User(oidcUser, ProviderProfile{providerUserId, email, emailVerified, displayName, avatarUrl})`.
   - At this stage we do **not** call `OAuthIdentityLinker` — we keep the filter chain symmetric; the success handler will do the linking.

### F3 — Linking / Creation (`OAuthIdentityLinker.link(...)`)

Inputs: `provider="google"`, `providerUserId` (sub), `email`, `emailVerified`, `name`, `picture`.

Decision tree:

| Case | Condition | Action |
|------|-----------|--------|
| A | `user_oauth_accounts` has row `(google, sub)` | Return existing `user_id`. |
| B | No oauth row, `email_verified=true`, `users.email = email` (case-insensitive) exists | Insert `user_oauth_accounts` row linked to that user. Return `user_id`. |
| C | No oauth row, no matching user | Call `RegisterUserCommand` (see F3.1). Insert `user_oauth_accounts` row. Return new `user_id`. |
| D | `email_verified=false` | Throw `OAuthEmailNotVerifiedException`. |

`OAuthIdentityLinker` returns a record `LinkedIdentity(userId, isNewUser, isLinkedToExisting)`.

#### F3.1 — New user construction

- `email` = Google `email` (verified).
- `email_verified` = `true` (set on the `users` row).
- `username`:
  - Take email local part, slugify (lowercase, keep `[a-z0-9_-]`, max 60 chars).
  - Append `_` + 6-char base36 random.
  - If unique-check fails, retry up to 5 times; if all fail, fall back to UUID-based suffix.
- `displayName`:
  - Use `name` from Google, trimmed, cap 120 chars.
  - Fallback to email local part if `name` is null/blank.
- `password_hash` = `null`.
- `status` = `PENDING_PROFILE` (new enum value, see §5).
- `avatar_url` stored in `user_oauth_accounts` (not on `users`) — keeps `users.avatar_url` for user-uploaded avatars.

`RegisterUserCommand` already exists; we extend it to accept `password=null, emailVerified=true, status=PENDING_PROFILE` (no new optional fields; `status` is already part of the command for password registration).

### F4 — Session issuance (`OAuthSessionIssuer.issue(userId, ClientContext)`)

1. Load user via `UserLookupService`.
2. Check `user.canAuthenticate()` (= `ACTIVE`). If `PENDING_PROFILE`, still allow OAuth login (deliberate: a brand-new OAuth user is allowed to finish setting up their profile).
   - Reject if `DISABLED` or `DELETED` (same as password flow).
3. Call `JwtService.issueTokens(user, sessionId, now)`.
4. Call `SessionService.createSession(user, tokenPair, context)` — this writes the `user_sessions` row, applies refresh-token rotation policy, and applies the 5-sessions-per-user cap.
5. Return `TokenPair` + `sessionId`.

`CookieService.writeTokenCookies(response, tokenPair)` then sets `fuoverflow_at` and `fuoverflow_rt` exactly like the password login endpoint.

### F5 — Success handler

```java
OAuthAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    void onAuthenticationSuccess(req, res, auth) {
        var principal  = (PrincipalOAuth2User) auth.getPrincipal();
        var linked     = oauthIdentityLinker.link(principal.profile());
        var bundle     = oauthSessionIssuer.issue(linked.userId(), context(req));
        cookieService.writeTokenCookies(res, bundle.tokenPair());
        var url = UriComponentsBuilder
                  .fromHttpUrl(oauth2Properties.successRedirect())
                  .queryParam("provider", "google")
                  .queryParam("new",      linked.isNewUser())
                  .queryParam("userId",   linked.userId())
                  .build().toUriString();
        res.sendRedirect(url);
    }
}
```

`successRedirect` default: `http://localhost:5173/auth/callback`. `new=true` means FE should route to `/onboarding/profile`; `new=false` means go to `/home`.

### F6 — Failure handler

Failure codes (stable strings, never include PII):

| Condition | Code |
|-----------|------|
| User canceled at Google | `OAUTH_CANCELED` |
| `email_verified=false` from Google | `OAUTH_EMAIL_NOT_VERIFIED` |
| `UserStatus.DISABLED` / `DELETED` | `OAUTH_USER_BLOCKED` |
| Any other OAuth error | `OAUTH_PROVIDER_ERROR` |

```java
res.sendRedirect(oauth2Properties.errorRedirect() + "?code=" + code);
```

`errorRedirect` default: `http://localhost:5173/auth/error`.

### F7 — Audit

Log lines (no PII beyond user_id, no id_token, no email content beyond length):

- `OAUTH_LOGIN_SUCCESS userId=<uuid> provider=google new=<bool> linked=<bool>` — INFO
- `OAUTH_LOGIN_FAILURE code=<OAUTH_…> provider=google` — WARN
- `OAUTH_LOGIN_ATTEMPT_FOR_PASSWORD_ONLY_USER userId=<uuid>` — WARN (if user with `password_hash=null` tries to log in via password later)

## 5. Database & Enum Changes

New Flyway migration `V30__add_google_oauth_user_fields.sql`:

```sql
-- 1) users.email_verified: replace email_verified_at semantics with explicit boolean.
--    Keep email_verified_at for legacy reasons (not removed).
alter table users add column email_verified boolean not null default false;

-- 2) users.status: extend check constraint to allow `pending_profile`.
alter table users drop constraint if exists users_status_check;
alter table users add constraint users_status_check
  check (status in ('active','pending','banned','deleted','pending_profile'));

-- 3) user_oauth_accounts: enrich row with profile snapshot + updated_at.
alter table user_oauth_accounts
  add column display_name varchar(255) null,
  add column avatar_url   varchar(512) null,
  add column updated_at   timestamptz not null default now();

-- 4) Helpful index for the linking case B (lookup user by email).
--    If a functional-unique index on lower(email) does not already exist on users,
--    we do not add one here (out of scope).
```

Notes:

- No foreign keys.
- The existing unique index `ux_oauth_provider_user(provider, provider_user_id)` is preserved.
- Email lowercasing on lookup is performed in application code (`EmailNormalizer` already exists in `user` module).

### Java entity / enum changes

- `UserStatus` enum: add `PENDING_PROFILE`.
- `UserStatus.canAuthenticate()`: keep returning `true` only for `ACTIVE`. OAuth flow special-cases `PENDING_PROFILE` to allow the user to finish onboarding; the helper itself stays strict.
- `UserEntity`: add `emailVerified` boolean field.
- `UserOAuthAccountEntity`: add `displayName`, `avatarUrl`, `updatedAt` fields.
- `UserOAuthAccountRepository`: add `findByProviderAndProviderUserId(...)` and `findByUserId(provider, userId)` methods.

## 6. Configuration

`application.yml` (excerpt):

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${OAUTH_GOOGLE_CLIENT_ID}
            client-secret: ${OAUTH_GOOGLE_CLIENT_SECRET}
            scope: openid,email,profile
            redirect-uri: "{baseUrl}/login/oauth2/code/google"

app:
  oauth2:
    success-redirect: ${OAUTH_SUCCESS_REDIRECT:http://localhost:5173/auth/callback}
    error-redirect:   ${OAUTH_ERROR_REDIRECT:http://localhost:5173/auth/error}
    frontend-base-url: ${OAUTH_FRONTEND_BASE_URL:http://localhost:5173}
```

`OAuth2Properties` is a `@ConfigurationProperties("app.oauth2")` record.

`SecurityConfig` change: add `.oauth2Login(o -> o.userInfoEndpoint(u -> u.oidcUserService(googleOAuth2UserService)).successHandler(oauthSuccessHandler).failureHandler(oauthFailureHandler))` and add `/oauth2/authorization/google`, `/login/oauth2/code/google` to the public matcher list.

`OAuth2AuthorizedClientService`: default in-memory. Multi-instance note documented in §8.

## 7. Security Checklist

- `email_verified=true` from `id_token` is the only path that creates/links users.
- `sub` is used as `provider_user_id` — never `email` (email can change).
- `client_secret` only used server-side, never logged, never returned to client.
- Spring `oauth2Login()` handles CSRF `state` generation and verification.
- `oidcUser.getEmail()` / `getName()` / `getPicture()` are read post-verification; no unverified claims.
- User disabled → session issuance fails with `USER_DISABLED` (consistent with password flow).
- Password login by OAuth-only user (`password_hash=null`) returns generic `INVALID_CREDENTIALS` and logs `OAUTH_LOGIN_ATTEMPT_FOR_PASSWORD_ONLY_USER`.
- No `id_token`, `access_token`, or `code` in logs; logs use a single `traceId` from existing `common` request filter.
- `users.email_verified` is updated to `true` whenever a verified Google email links/creates a user.
- The `password_hash=null` invariant is preserved on user creation; password is never generated server-side.

## 8. API Contracts for FE

### New endpoints (auto-registered by Spring)

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/oauth2/authorization/google` | none | Spring redirect to Google |
| GET | `/login/oauth2/code/google` | none | Spring callback; sets cookies + redirects |

### Public response contract

- Success redirect: `{app.oauth2.success-redirect}?provider=google&new=<true|false>&userId=<uuid>`
- Error redirect: `{app.oauth2.error-redirect}?code=<OAUTH_…>`
- Cookies: `fuoverflow_at`, `fuoverflow_rt` (existing semantics).
- Follow-up: `GET /api/v1/users/me` returns the user profile; FE can detect `status=PENDING_PROFILE` and route to onboarding.

### Wire format for `LinkedIdentity` (internal only, not exposed)

```java
record LinkedIdentity(UUID userId, boolean isNewUser, boolean isLinkedToExisting) {}
```

## 9. Test Plan

### Unit

- `OAuthIdentityLinkerTest`:
  - Case A — existing oauth row → returns userId, `isNewUser=false`, `isLinkedToExisting=false`.
  - Case B — verified email match → inserts oauth row, returns userId, `isNewUser=false`, `isLinkedToExisting=true`.
  - Case C — new user → `RegisterUserCommand` called with `password=null`, `emailVerified=true`, `status=PENDING_PROFILE`; oauth row inserted; `isNewUser=true`.
  - Case D — `email_verified=false` → throws `OAuthEmailNotVerifiedException`.
  - Case C-username collision → retry loop produces a valid username within 5 attempts.
  - Case C-name null → falls back to email local part.
- `OAuthSessionIssuerTest` (with mocked `JwtService` and `SessionService`):
  - Active user → returns TokenPair.
  - Disabled user → throws.
  - Pending-profile user → returns TokenPair.
- `OAuthAuthenticationSuccessHandlerTest`:
  - Builds correct redirect URL (provider, new, userId).
  - Calls `CookieService.writeTokenCookies`.
- `OAuthAuthenticationFailureHandlerTest`:
  - Maps each exception to the right `OAUTH_…` code.

### MVC / Security integration

- `OAuth2LoginFlowIT` (full Spring context, Google auto-config disabled; substitute `OAuth2UserService` with a stub that returns a hand-crafted `OidcUser`):
  - Full happy path: GET `/oauth2/authorization/google` → 302 to Google stub → GET `/login/oauth2/code/google` → 302 to FE success URL with cookies set → `GET /api/v1/users/me` returns the user.
  - Email not verified → redirect to error URL with `code=OAUTH_EMAIL_NOT_VERIFIED`.
  - Disabled user → redirect to error URL with `code=OAUTH_USER_BLOCKED`.
  - Re-login (case A): second visit does not create a second oauth row.

### Manual smoke

- Local dev: set `OAUTH_GOOGLE_CLIENT_ID`, `OAUTH_GOOGLE_CLIENT_SECRET` from Google Cloud Console (Authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`).
- Click "Login with Google" → land on Google consent → land back at `/api/v1/users/me` authenticated.

## 10. Migration / Rollout

1. Apply migration `V30__add_google_oauth_user_fields.sql`. Backward-compatible: existing rows get `email_verified=false`, `status` values remain in the original set.
2. Deploy BE with `app.oauth2.*` config + Google client credentials.
3. Flip FE entry point to point at `/oauth2/authorization/google` (FE work, out of scope for this spec).
4. Monitor `OAUTH_LOGIN_FAILURE` rate and 5xx rate on `/login/oauth2/code/google` for 24h.

### Rollback

- Disable the FE entry point.
- The OAuth paths are additive; nothing existing is changed.
- Migration V30 is additive; dropping it is unnecessary for rollback because new code does not run.

## 11. Future Work (out of scope this round)

- Account unlink endpoint (`DELETE /api/v1/users/me/oauth/:provider`).
- Additional providers (Facebook, GitHub, Apple).
- Multi-instance `JdbcOAuth2AuthorizedClientService` if the deployment ever spans more than one BE pod.
- Consent screen explanation text in the Google Cloud project.
- FE onboarding flow for `PENDING_PROFILE` users.

## 12. Risk Register

| Risk | Mitigation |
|------|------------|
| Email collision in case B takes over someone else's account | Require `email_verified=true` from Google; only links if a local user with that verified email exists; never take over an account whose `email_verified=false`. |
| Username collision in case C | Retry loop with 6-char base36 suffix; fall back to UUID suffix. |
| `users_status_check` constraint update breaks existing rows | New value only; existing `active/pending/banned/deleted` rows unaffected. |
| `client_secret` leaked via logs | Confine logging to user_id; document no-secret-in-logs policy in code review checklist. |
| Multi-instance BE | For MVP single instance, in-memory `OAuth2AuthorizedClientService` is fine; long-lived state lives in `user_sessions`. |
| Google rotates JWKS | Spring's `JwkSetUriJwtDecoder` auto-refreshes; nothing custom here. |
| `sub` is empty/null in id_token | Defensive guard in `GoogleOAuth2UserService` — throw `OAuthProviderErrorException` mapped to `OAUTH_PROVIDER_ERROR`. |
