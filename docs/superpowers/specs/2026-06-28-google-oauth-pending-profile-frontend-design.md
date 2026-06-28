# Google OAuth Pending Profile Frontend — Design

Date: 2026-06-28
Status: Approved in chat (pending written spec review)
Owner: frontend
Related: `docs/superpowers/specs/2026-06-22-google-oauth-login-design.md`, `.knowledge/auth-module-plan.md`

## 1. Goals & Non-Goals

### In scope

- Add a dedicated frontend completion flow for Google OAuth users whose account status is `PENDING_PROFILE`.
- Show a focused form that collects the missing fields required after Google signup: `username`, `campus`, `displayName`.
- Reuse the same validation rules already used by the normal registration screen for those three fields where applicable.
- Submit the form to a backend endpoint that completes the current authenticated user's profile.
- Handle backend username uniqueness validation on submit and show the error under the `username` field.
- Redirect successful completion to `/suoc`.
- Guard navigation so authenticated `PENDING_PROFILE` users are forced into the completion screen until they finish.

### Out of scope

- Realtime username availability checks.
- Suggested usernames.
- Additional onboarding steps.
- Avatar upload or extra profile fields.
- Changing the normal email/password registration flow beyond extracting shared validation helpers if useful.

## 2. Context Snapshot

Current relevant frontend files:

- `Fuexam/app/(auth)/login/page.tsx`
- `Fuexam/app/(auth)/register/page.tsx`
- `Fuexam/lib/auth/AuthProvider.tsx`

Current relevant backend fact:

- `backend/user/src/main/java/com/fuoverflow/user/domain/UserStatus.java` allows `PENDING_PROFILE` users to authenticate via `canAuthenticate()`.

Current Google OAuth frontend behavior is minimal: login/register pages redirect the browser to `${API_BASE}/oauth2/authorization/google`, then frontend auth state is derived later by `AuthProvider` fetching the current user profile.

That means the frontend already has enough information to detect `status=PENDING_PROFILE` after OAuth login; it just lacks a dedicated completion route, routing guards, and submit flow.

## 3. Recommended Approach

### Recommended: dedicated `/complete-profile` route

Use a standalone route for the pending-profile flow rather than overloading the existing register page or using a modal gate.

Why this is the best fit:

- Clear user intent: this is not “register” again; it is “complete profile”.
- Cleaner separation of concerns: password signup and OAuth completion are different flows.
- Easier routing and guard logic: `PENDING_PROFILE` → one destination.
- Easier future extension: additional onboarding fields or steps can be added without complicating `/register`.

### Alternatives considered

#### A. Reuse `/register` with an OAuth mode

Pros:
- Reuses more UI in one place.

Cons:
- Turns one page into two different products.
- Adds branching around hidden password/email fields.
- Makes labels and submit semantics less clear.

#### B. Global modal/full-screen blocker after login

Pros:
- Keeps login redirect path mostly unchanged.

Cons:
- More complex to enforce consistently.
- Harder to reason about on refresh/navigation.
- Less explicit than a dedicated route.

## 4. User Flow

### F1 — Google OAuth start

From either:

- `Fuexam/app/(auth)/login/page.tsx`
- `Fuexam/app/(auth)/register/page.tsx`

The user clicks the Google button and is redirected to backend OAuth kickoff as today.

### F2 — Session established

After backend OAuth success, token cookies are set and frontend auth state is hydrated through the existing current-user fetch path in `AuthProvider`.

### F3 — Pending-profile detection

When frontend knows the authenticated user:

- If `user.status === "PENDING_PROFILE"` → route to `/complete-profile`
- If `user.status === "ACTIVE"` → continue normal flow

### F4 — Completion form

At `/complete-profile`, render a focused form with:

- `username`
- `campus`
- `displayName`

Optional display-only context:

- Show the authenticated email as read-only helper text if already available in the current user payload.

### F5 — Submit behavior

On submit:

1. Run client-side validation for `username`, `campus`, `displayName`.
2. Call the backend complete-profile endpoint for the current user.
3. If backend returns username conflict, map that error to the `username` field.
4. If success, refresh current user state.
5. Redirect to `/suoc`.

## 5. Routing & Guard Design

## 5.1 New route

Add a new auth route:

- `Fuexam/app/(auth)/complete-profile/page.tsx`

This route is the only valid destination for authenticated users who still have `PENDING_PROFILE` status.

## 5.2 Route entry rules

At `/complete-profile`:

- Unauthenticated user → redirect `/login`
- Authenticated user with `ACTIVE` status → redirect `/suoc`
- Authenticated user with `PENDING_PROFILE` → render form

## 5.3 Global pending-profile gate

Add a lightweight frontend redirect rule outside `AuthProvider` itself:

- If the app knows the user is authenticated and `status === "PENDING_PROFILE"`
- and the current route is not `/complete-profile`
- then redirect to `/complete-profile`

This prevents partially onboarded OAuth users from entering the rest of the app before finishing required profile fields.

## 5.4 Why routing stays outside `AuthProvider`

`AuthProvider` should remain responsible for session/user state, not application navigation policy.

Benefits:

- Keeps provider predictable.
- Avoids hidden redirects triggered by background refreshes.
- Makes per-page auth behavior easier to inspect and maintain.

## 6. UI & Form Design

## 6.1 Page content

Page title and copy should clearly signal that the account already exists and only missing information is required.

Recommended tone:

- Eyebrow: “Hoàn tất tài khoản”
- Heading: “Bổ sung thông tin còn thiếu” or “Hoàn tất hồ sơ”
- Supporting text: explain that this information is needed before entering the community.

## 6.2 Fields

### `username`

Rules should match current registration behavior:

- required
- minimum 3 characters
- maximum 32 characters
- lowercase only
- allowed pattern: `a-z`, `0-9`, `_`

### `campus`

Rules:

- required
- selected from the existing `FPT_CAMPUSES` source

### `displayName`

Rules:

- required
- minimum 2 characters
- maximum 80 characters

## 6.3 Error presentation

- Field errors render directly below the field, consistent with register page behavior.
- Username uniqueness error from backend should appear under `username`, not only in a top-level banner.
- Non-field/server errors should appear in the form-level error box used by the existing auth pages.

## 6.4 Submit state

- Disable submit while the request is in flight.
- Keep entered values intact on validation/server errors.
- Success should not show an intermediate success page; redirect directly to `/suoc`.

## 7. Component Structure

Prefer targeted reuse over merging the whole register screen into a multi-mode form.

Recommended structure:

- New page: `Fuexam/app/(auth)/complete-profile/page.tsx`
- Shared validation/helper extraction for:
  - `username`
  - `campus`
  - `displayName`
- Shared presentational helper components can be extracted if it reduces duplication cleanly.

Important constraint:

- Do not convert the current register page into a giant dual-purpose component unless the resulting code is clearly simpler.

The right level of reuse is likely shared validation and small UI helpers, not a single “do everything” auth form.

## 8. API Contract Expectations

Frontend expects a dedicated authenticated endpoint for completing the current user's profile.

Recommended semantics:

- Method: `POST` or `PATCH`
- Auth: required
- Target: current authenticated user only
- Allowed only when current status is `PENDING_PROFILE`

Request payload:

```json
{
  "username": "nguyen_van_a",
  "campus": "HCM",
  "displayName": "Nguyễn Văn A"
}
```

Backend behavior required by the frontend design:

- validate username format
- validate username uniqueness
- validate campus value
- validate display name length
- update the user profile
- transition status from `PENDING_PROFILE` to `ACTIVE`

Response can follow existing project conventions; frontend only needs enough signal to treat the request as successful and then refresh the current user.

### Error mapping expectation

Preferred response handling for FE:

- Username conflict → field-level error on `username`
- Validation failure on one field → field-level error where possible
- Generic server failure → form-level error banner

The frontend implementation may need a small adapter if current backend error envelopes are generic.

## 9. State Management Behavior

Use the existing auth model:

- `AuthProvider` remains the source of truth for `user` and `loading`
- completion submit calls the new API client function
- on success call `refreshUser()`
- after refreshed user becomes `ACTIVE`, redirect `/suoc`

This keeps the flow aligned with the rest of the app and avoids shadow auth state.

## 10. Edge Cases

- User refreshes the browser while on `/complete-profile` → page rehydrates and still allows completion.
- User manually opens `/complete-profile` after already becoming `ACTIVE` → redirect `/suoc`.
- User manually opens `/complete-profile` while logged out → redirect `/login`.
- Backend returns username conflict after submit → show field error, preserve all form values.
- User with `PENDING_PROFILE` tries to access another page directly → redirected back to `/complete-profile`.

## 11. Test Plan

### Frontend unit/component coverage

- Validation for `username`, `campus`, `displayName`
- Submit success flow: API called, user refreshed, redirect `/suoc`
- Username conflict response mapped to `username` field error
- Generic API failure mapped to form-level error

### Frontend route behavior coverage

- Logged-out access to `/complete-profile` redirects `/login`
- `ACTIVE` user at `/complete-profile` redirects `/suoc`
- `PENDING_PROFILE` user at `/complete-profile` sees the form
- `PENDING_PROFILE` user visiting other app routes is redirected to `/complete-profile`

### Backend coverage required to support this FE

- Only `PENDING_PROFILE` users can complete profile
- Username uniqueness enforced
- Success updates fields and changes status to `ACTIVE`
- Invalid campus / invalid username returns validation error

## 12. Implementation Boundaries

This design intentionally stays focused.

Included:
- frontend route
- frontend guards
- frontend form
- API integration for completion
- backend support needed specifically for the completion endpoint and status transition

Not included:
- broader onboarding redesign
- social login provider expansion
- account linking UX
- username suggestion systems

## 13. Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Redirect loops for pending-profile users | Exempt `/complete-profile` itself from the pending-profile gate. |
| Over-coupling register page and completion page | Reuse only small helpers unless a shared form is demonstrably simpler. |
| Backend error envelope not field-specific | Add a frontend adapter or minimal backend error code contract for username conflict. |
| User reaches app content before guard runs | Apply redirect as early as practical in page/layout-level auth-aware code. |
| Completion route becomes inaccessible after auth refresh timing issues | Base route rendering on `loading` + `user` state and avoid premature redirects before hydration finishes. |

## 14. Success Criteria

The feature is complete when all of the following are true:

1. A Google OAuth user with `PENDING_PROFILE` is taken to `/complete-profile` instead of the main app.
2. The screen collects `username`, `campus`, and `displayName` only.
3. Username uniqueness is checked only on submit, and collisions appear as a field error.
4. Successful completion updates auth-visible user state to `ACTIVE`.
5. The user is redirected to `/suoc`.
6. Pending-profile users cannot continue into the app without finishing this step.
