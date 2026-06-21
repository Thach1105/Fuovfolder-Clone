# `/me/points` page design

## Goal

Add a real user-facing `/me/points` page in the Fuexam Next.js app so the existing membership CTA no longer lands on a 404 page. The page should let an authenticated user:

- see their current FUO Point balance,
- review recent points ledger entries,
- navigate to the existing deposit flow at `/deposit`.

This design intentionally stays narrow. It does not add new backend APIs, new filtering, or admin functionality.

## Existing context

Relevant existing code:

- Membership page links to `/me/points` at `Fuexam/app/(app)/membership/page.tsx`.
- The public deposit flow already exists at `Fuexam/app/(app)/deposit/page.tsx`.
- Frontend API client already exposes:
  - `getPointsBalance()`
  - `getPointsLedger(page, size)`
  in `Fuexam/lib/api/points.ts`.
- Header already shows a lightweight points badge in `Fuexam/components/layout/PointsBalanceBadge.tsx`.

The current bug is route-level, not API-level: the app links to `/me/points`, but no page file exists for that route.

## Recommended approach

### Option A — focused page using existing clients (recommended)

Create `Fuexam/app/(app)/me/points/page.tsx` as a client page that loads balance and ledger with the existing API client functions.

Why this is recommended:

- fixes the broken route with minimal scope,
- reuses existing API surface,
- matches current app patterns (`useEffect`, local state, `useAuth`, `ApiError`),
- avoids adding backend work or speculative abstractions.

### Option B — redirect `/me/points` to `/deposit`

This would remove the 404, but it would not satisfy the route’s implied purpose of showing a user’s point status/history. It is too thin now that the API already supports balance and ledger.

### Option C — larger wallet dashboard

This could combine balance, ledger, deposit tiers, and purchase history in one place. It is not recommended for this task because it expands scope and duplicates the existing `/deposit` page.

## Page behavior

### Route and auth

- Route: `/me/points`
- File: `Fuexam/app/(app)/me/points/page.tsx`
- The page will be a client component.
- If the auth provider finishes loading and there is no authenticated user, redirect to `/login?next=/me/points`.

### Data loading

On initial load for an authenticated user:

1. load current points balance with `getPointsBalance()`,
2. load first ledger page with `getPointsLedger(0, 20)`.

Pagination will remain server-driven using the existing response fields:

- `page`
- `size`
- `totalElements`
- `totalPages`

The page will support simple previous/next pagination only. No page-size chooser, no filters, no search.

### UI structure

#### 1. Header section

- Title: `FUO Point của tôi`
- Short helper text explaining this page shows current balance and transaction history.

#### 2. Balance card

A prominent card near the top showing:

- current FUO balance,
- optional small label such as `Số dư hiện tại`,
- primary CTA button: `Nạp thêm FUO` linking to `/deposit`.

#### 3. Ledger section

A list/table of recent ledger entries showing:

- delta amount,
- reason,
- source type,
- source id when present,
- created time formatted for Vietnamese locale.

Visual treatment:

- positive delta uses success styling,
- negative delta uses danger styling,
- zero delta uses neutral styling.

The layout can be a responsive stacked list or simple table depending on what fits existing page patterns best. The key requirement is readability on both mobile and desktop.

#### 4. Pagination controls

- `Trước`
- `Sau`
- current page indicator

Disable controls at boundaries and during page fetch.

## Loading, empty, and error states

### Loading

- show a lightweight loading state while initial data loads,
- show a smaller in-section loading state during page changes.

### Empty state

If the ledger has no items, show a clear empty message, for example:

`Chưa có giao dịch FUO Point nào.`

The deposit CTA should still remain available.

### Error handling

- Use the existing `ApiError` pattern already used by membership and deposit pages.
- If initial load fails, show a visible inline error banner.
- If ledger pagination fails, keep the previous page data on screen when practical and show an error banner/message instead of blanking the whole page.

## Data formatting

- Reuse existing points formatting style where practical so the page matches the header badge.
- Format timestamps with Vietnamese locale.
- Preserve backend-provided `reason` and `sourceType` strings as display text unless obviously empty; if missing, fall back to a safe placeholder like `Không rõ`.

## Scope boundaries

Included:

- new `/me/points` page,
- authenticated balance display,
- paginated ledger display,
- deposit CTA.

Excluded:

- new backend endpoints,
- ledger filtering/sorting UI,
- source detail linking,
- export/download,
- admin adjustments UI,
- merging deposit tiers into this page.

## Verification

Manual verification should cover:

1. authenticated user with non-zero balance and at least one ledger row,
2. authenticated user with empty ledger,
3. unauthenticated visit redirects to login with `next=/me/points`,
4. CTA navigates to `/deposit`,
5. membership page link to `/me/points` no longer 404s because the route now exists.

## Implementation impact

Expected file additions/changes:

- add `Fuexam/app/(app)/me/points/page.tsx`
- possibly add a small helper or inline formatter in the same file if needed
- no backend changes expected
- no API client changes expected unless a tiny typed helper improves readability
