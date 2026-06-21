# Deposit tier config design

Date: 2026-06-21
Project: FuOverflow / Fuexam
Status: Draft approved in conversation

## Goal

Add backend support for configurable user deposit tiers so admins can manage fixed VND packages that convert to points. Users will later choose one predefined tier when creating a PayOS payment link. The awarded points must be snapshotted into the order at creation time so later admin edits do not change the points for already-created orders.

## Decisions already confirmed

- Conversion model: fixed deposit tiers, not free-form exchange rate.
- Scope: global `deposit_tiers` table, independent from membership plans.
- Payment snapshot strategy: snapshot tier-derived points into the order at payment-link creation time.
- Architectural approach: create a new backend module `deposit` instead of expanding `payment` or `membership`.
- Currency scope: VND only for MVP.
- Admin activation workflow: create tiers directly as active; admins can disable them later.

## Current project context

Relevant existing pieces:

- `backend/payment` already integrates with PayOS and creates `orders`, `payments`, and `payment_webhook_events`.
- `backend/payment/application/PointService.java` already manages `point_balances` and `point_transactions`.
- `backend/payment/application/PaymentService.java` currently hardcodes points credit with `order.getTotalCents() / 100`.
- `backend/membership` already demonstrates the admin CRUD pattern using `@RequirePermission`, DTOs, service, repository, and controller layers.
- `V26__add_points_system.sql` added the points tables and extended payment statuses.

## Proposed architecture

Create a new module:

```text
backend/deposit/
  src/main/java/com/fuoverflow/deposit/
    api/
      DepositTierAdminController.java
      DepositTierController.java
      dto/
        AdminDepositTierResponse.java
        CreateDepositTierRequest.java
        UpdateDepositTierRequest.java
        ToggleDepositTierRequest.java
        DepositTierResponse.java
    application/
      DepositTierAdminService.java
      DepositTierQueryService.java
    persistence/
      DepositTierEntity.java
      DepositTierRepository.java
    config/
      DepositModule.java
```

Responsibilities:

- `deposit` owns the tier catalog and exposes admin/user read APIs.
- `payment` remains responsible for PayOS link creation, webhook confirmation, orders, and payment records.
- `payment` depends on `deposit` only to resolve the selected tier at order creation time.
- Awarded points are then read from the order snapshot, not from `deposit_tiers`, during webhook confirmation.

This keeps the tier catalog as a distinct business concept instead of mixing it into gateway integration logic.

## Data model

### New table: `deposit_tiers`

Columns:

- `id uuid primary key`
- `label varchar(120) not null`
- `amount_vnd int not null`
- `points int not null`
- `bonus_percent int not null default 0`
- `is_active boolean not null default true`
- `sort_order int not null default 0`
- `lock_version int not null default 0`
- `created_at timestamptz not null default now()`
- `updated_at timestamptz not null default now()`

Constraints:

- `amount_vnd > 0`
- `points >= 0`
- `bonus_percent between 0 and 100`
- `sort_order >= 0`
- partial unique index on active amount to prevent two active tiers with the same VND amount:
  - `unique(amount_vnd) where is_active = true`

Computed value:

- `total_points` is not persisted as a column in MVP; it is derived in application code:
  - `total_points = points + floor(points * bonus_percent / 100.0)`

Reasoning:

- Derived storage is avoided to keep write logic simpler and remove generated-column portability concerns.
- `bonus_percent` remains explicit for admin readability and marketing use.

### Changes to `orders`

Add nullable snapshot columns:

- `tier_id uuid null`
- `points_awarded int null`
- `tier_label_snapshot varchar(120) null`

No foreign key is added, matching the project rule that cross-table references are validated in application code rather than DB constraints.

Snapshot rules:

- `tier_id` stores the selected tier identity for audit/debug.
- `points_awarded` stores the exact credited points for this order.
- `tier_label_snapshot` stores the display label the user saw when the order was created.

## Payment flow

### User-facing flow

1. FE calls `GET /api/v1/deposit/tiers`.
2. User selects one active tier.
3. FE calls `POST /api/v1/payment/create` with `tierId`, `returnUrl`, and `cancelUrl`.
4. `PaymentService` looks up the tier, validates it is active, calculates the snapshot, creates the PayOS link, and stores an order with tier snapshot fields.
5. FE redirects to `checkoutUrl` or renders the returned `qrCode`.
6. PayOS sends webhook on payment completion.
7. Webhook confirmation creates a `PaymentEntity`, marks the order paid, and credits the snapshotted points.
8. FE polls `GET /api/v1/payment/status?orderCode=...` to show success/failure.

### Snapshot logic in payment service

The current `createPaymentLink` signature:

```java
createPaymentLink(BigDecimal amount, String description, String returnUrl, String cancelUrl, UUID userId)
```

will change to a tier-based input, effectively:

```java
createPaymentLink(UUID tierId, String returnUrl, String cancelUrl, UUID userId)
```

Behavior:

- Look up active tier by `tierId`.
- Use `tier.amount_vnd` as the PayOS amount.
- Build idempotency key from `userId|tierId|returnUrl|cancelUrl`.
- Create the PayOS payment link.
- Persist `OrderEntity` with:
  - `total_cents = amount_vnd`
  - `currency = VND`
  - `provider = payos`
  - `provider_order_id = orderCode`
  - `tier_id = selected tier id`
  - `points_awarded = total_points`
  - `tier_label_snapshot = tier.label`

### Points credit logic

Current logic in `PaymentService.creditPointsForPaidOrder`:

```java
long points = order.getTotalCents() / 100;
```

New logic:

- If `order.points_awarded != null`, use that value.
- If `order.points_awarded == null`, fall back to the legacy formula `order.getTotalCents() / 100` for backward compatibility with older orders.

This prevents historical orders from breaking after rollout.

## API design

### Admin APIs

Base path: `/api/v1/admin/deposit-tiers`

Permissions:

- read: `deposit.admin:read`
- write: `deposit.admin:update`
- controllers remain guarded by `@RequirePermission("admin.panel:access")`

Endpoints:

- `GET /api/v1/admin/deposit-tiers`
  - list all tiers, including inactive ones
- `GET /api/v1/admin/deposit-tiers/{tierId}`
  - get one tier
- `POST /api/v1/admin/deposit-tiers`
  - create a new tier
- `PUT /api/v1/admin/deposit-tiers/{tierId}`
  - update all editable fields
- `PATCH /api/v1/admin/deposit-tiers/{tierId}/toggle`
  - enable/disable a tier
- no hard delete endpoint in MVP

Admin response fields:

- `id`
- `label`
- `amountVnd`
- `points`
- `bonusPercent`
- `totalPoints`
- `isActive`
- `sortOrder`
- `createdAt`
- `updatedAt`

### User APIs

Base path: `/api/v1/deposit`

Endpoints:

- `GET /api/v1/deposit/tiers`
  - return active tiers only
  - sort by `sort_order asc`, then `amount_vnd asc`

User response fields:

- `id`
- `label`
- `amountVnd`
- `totalPoints`
- `bonusPercent`
- `sortOrder`

The user API intentionally hides inactive tiers and admin-only audit fields.

### Payment API changes

`POST /api/v1/payment/create` request contract changes from amount-driven to tier-driven.

New request fields:

- `tierId`
- `returnUrl`
- `cancelUrl`

Removed from user control:

- `amount`
- free-form `description`

Description can instead be generated by backend from the selected tier label or a stable format such as `Nap diem <label>`.

## Validation rules

### CreateDepositTierRequest

- `label`: required, max 120 chars
- `amountVnd`: required, min 1_000, max 100_000_000
- `points`: required, min 0
- `bonusPercent`: required, min 0, max 100
- `sortOrder`: required, min 0
- `isActive`: optional; defaults true if omitted

### UpdateDepositTierRequest

Same field constraints as create. Update remains full-update (`PUT`) in MVP for simplicity.

### Payment create request

- `tierId`: required UUID
- `returnUrl`: required nonblank URL
- `cancelUrl`: required nonblank URL

## Error handling

| Case | Result |
|---|---|
| Tier not found | `404 TIER_NOT_FOUND` |
| Tier inactive | `404 TIER_NOT_FOUND` to avoid leaking inactive catalog details |
| Duplicate active amount | DB conflict -> translated to validation/business error |
| PayOS link creation fails | propagate application error, rollback order creation |
| Webhook on old order with null snapshot | fallback credit formula |
| Webhook on unknown order | existing controller behavior remains graceful and returns 200 for PayOS connectivity-test/stale cases |

## Concurrency and lifecycle behavior

- `DepositTierEntity` uses `@Version` on `lock_version` to prevent lost updates.
- Admin can update active tiers at any time.
- Existing pending orders are safe because points are snapshotted.
- Tiers are never hard-deleted in MVP; deactivation happens via `is_active=false`.
- A tier can be disabled even if historical orders reference it.

## RBAC changes

Migration should seed two new permissions into the existing RBAC catalog:

- `deposit.admin:read`
- `deposit.admin:update`

They should be granted to the appropriate admin role(s) using the same seeding pattern already used for membership and RBAC permissions.

## Migration plan

Create `V28__add_deposit_tiers.sql`.

Contents:

1. Create `deposit_tiers`
2. Add indexes and constraints
3. Alter `orders` to add:
   - `tier_id`
   - `points_awarded`
   - `tier_label_snapshot`
4. Seed RBAC permissions for deposit tier administration

Migration notes:

- all new order columns are nullable for backward compatibility
- no foreign key constraints are added
- existing rows in `orders` are not backfilled; legacy fallback handles them

## Testing strategy

### Service tests

- `DepositTierAdminServiceTest`
  - create tier
  - update tier
  - toggle tier active/inactive
  - reject duplicate active amount
- `DepositTierQueryServiceTest`
  - return active-only tiers sorted correctly
- `PaymentServiceDepositTierTest`
  - create link from tier
  - snapshot `points_awarded`
  - reject inactive tier
  - idempotency uses `tierId`
- `PaymentServiceWebhookSnapshotTest`
  - credit from snapshot for new orders
  - fallback to legacy formula for old orders

### MVC/security tests

- admin controller requires `admin.panel:access`
- admin list/create/update/toggle endpoints enforce deposit permissions
- user tier endpoint returns active tiers only

### Integration tests

One focused integration test can cover:

- seed tier
- create payment order
- simulate successful confirmation/webhook path
- verify:
  - order becomes paid
  - payment row exists
  - point balance increases by snapshotted amount
  - point transaction is written

## Rollout and compatibility

- Single PR rollout is acceptable.
- Existing payment success/cancel pages remain valid.
- Existing orders and webhooks continue to work because of `points_awarded` fallback behavior.
- FE user deposit page is intentionally out of scope for this backend spec but is the immediate next consumer of `GET /api/v1/deposit/tiers`.

## Out of scope

- FE `/deposit` UI implementation
- multi-currency support
- promotional time windows
- tier bundles tied to roles or memberships
- hard delete for tiers
- public purchase history page for deposit tiers

## Recommendation summary

Implement a new `deposit` module with a global `deposit_tiers` table and order-level point snapshots. This keeps payment gateway logic separate from business catalog management, prevents admin edits from changing already-created orders, and fits the project’s modular-monolith architecture and existing admin CRUD patterns.
