# FuOverflow Backend

Base Java Spring Boot monolith backend scaffold.

## Modules

- `app`: Spring Boot entrypoint and runtime configuration.
- `common`: shared config, validation, security, persistence, web primitives.
- `auth`, `user`, `forum`, `thread`, `post`, `reaction`, `notification`, `award`, `coursera`, `membership`, `course`, `material`, `payment`, `search`, `moderation`, `admin`: feature modules.
- `worker`: async worker placeholders for outbox, cache invalidation, counters, notifications, search indexing, file cleanup.

## Local services

```bash
docker compose up -d postgres redis minio minio-init
```

Object storage (MinIO, S3-compatible) serves uploaded media. Default local settings are in `application.yml` under `fuoverflow.storage.s3`. MinIO console: http://localhost:9001 (user `fuoverflow` / `fuoverflow_minio_dev`).

To use local disk instead of MinIO, set `STORAGE_PROVIDER=local`.

## Build

```bash
mvn clean package
```

## Run (script)

Same steps as manual workflow: `mvn clean` → `mvn install package` → `cd app` → `mvn spring-boot:run`.

Windows (PowerShell):

```powershell
.\run.ps1
.\run.ps1 -SkipDocker      # DB/Redis already running
.\run.ps1 -SkipTests       # mvn install package -DskipTests
.\run.ps1 -SkipClean       # skip mvn clean
```

Git Bash / Linux / macOS:

```bash
chmod +x run.sh
./run.sh
./run.sh --skip-docker --skip-tests
```

## Run (manual)

```bash
mvn -pl app spring-boot:run -Dspring-boot.run.profiles=local
```

## Health

```txt
GET http://localhost:8080/actuator/health
```

## Coursera service (FUO Point)

- User APIs: `/api/v1/coursera/catalog`, `/api/v1/coursera/requests`, `/api/v1/me/points/balance`
- Admin APIs: `/api/v1/admin/coursera/catalog`, `/api/v1/admin/coursera/requests`, `/api/v1/admin/users/{userId}/points/adjust`
- Migrations: `V5__coursera_service.sql`, seed catalog `V6__seed_coursera_catalog.sql`

### Environment

Set a strong key in production (32+ characters recommended):

```bash
COURSERA_CREDENTIALS_ENCRYPTION_KEY=your-secret-key-here
```

Local default is in `application.yml` under `fuoverflow.coursera.credentials.encryption-key`.

### Local test flow

1. Start Postgres/Redis and run the app.
2. Login as admin (seed user from `application-local.yml`).
3. Grant points: `POST /api/v1/admin/users/{userId}/points/adjust` with body `{"delta":500000,"reason":"dev"}`.
4. Create a Coursera request from the frontend at `/coursera`.

### Coursera request status and refunds

| From | To | FUO Point refund |
|------|-----|------------------|
| `pending` | `in_progress`, `cancelled` | Refund on `cancelled` if `fuoverflow.coursera.refund-on-cancel=true` (default) |
| `in_progress` | `completed`, `cancelled` | Refund on `cancelled` (full amount, once per order) |
| `completed`, `cancelled` | — | Terminal; no further changes |

Refund is idempotent: stored on `coursera_service_requests.refund_ledger_id`. Requires `payment_ledger_id` from order creation.

Admin UI uses action buttons (not a free-form status dropdown) and confirms cancel with refund amount.

Admin order list filters: `period` (`7d`, `30d`, `90d`), `userId`, `catalogItemId`, `status`. Summaries include `createdAt`, `statusChangedAt`, and `username`.

### Coursera DB performance (V7 indexes)

Migration: `V7__coursera_indexes.sql`

| Index | Table | Purpose |
|-------|-------|---------|
| `ix_coursera_requests_created_desc` | `coursera_service_requests` | Admin list sort by time |
| `ix_coursera_requests_user_status_created` | `coursera_service_requests` | Filter customer + status + time |
| `ix_coursera_request_items_catalog_request` | `coursera_request_items` | Admin filter by course |
| `ix_coursera_catalog_active_featured_sort` | `coursera_catalog_items` | Public featured catalog |
| `ix_coursera_catalog_live_sort` | `coursera_catalog_items` | Admin catalog sort |

List endpoints batch-load items, catalog codes, and usernames via `CourseraRequestSummaryAssembler` (avoids N+1).

Verify plans after deploy:

```bash
psql -f backend/scripts/coursera_explain_checks.sql
```

Catalog text search (`LIKE %q%`) is not B-tree indexed; add `pg_trgm` only if catalog grows large and search is slow.

## Source (Suộc) module

Module `source` sells exam-material packages priced in FUO Point. Migrations: `V8__source_service.sql`, `V9__source_indexes.sql`, `V10__seed_source_catalog.sql`.

API surface:

| Scope | Endpoint |
|-------|----------|
| Public | `GET /api/v1/source/catalog`, `GET /api/v1/source/catalog/featured`, `GET /api/v1/source/catalog/{idOrCode}` |
| User | `POST /api/v1/source/purchases` (header `Idempotency-Key`), `GET /api/v1/source/purchases?filter=active|expired|all`, `GET /api/v1/source/purchases/stats`, `GET /api/v1/source/purchases/{id}` |
| Admin (ADMIN, SUB_ADMIN) | `GET/POST/PUT/DELETE /api/v1/admin/source/catalog`, `PUT /api/v1/admin/source/catalog/{id}/related`, `GET /api/v1/admin/source/purchases`, `GET /api/v1/admin/source/overview` |
| Admin (ADMIN only) | `POST /api/v1/admin/source/purchases/{id}/refund` |

Payment safety and refunds:

- Purchase runs in one transaction: validate eligible user, debit FUO Point via `PointsWalletService.debit(...)`, then persist an `active` purchase with `starts_at`/`ends_at` from the catalog `access_days`.
- `Idempotency-Key` plus unique index `ux_source_purchases_idempotency (user_id, idempotency_key)` returns the existing purchase instead of charging twice; a race that loses the unique-index check is caught and resolved to the persisted row.
- Buying an item the user still has active access to returns **409** `ACTIVE_ACCESS_REMAINS` with message *Source này vẫn còn thời gian sử dụng*; after access expires, purchase works normally.
- Refund (`fuoverflow.source.refund-enabled=true`) credits the snapshot `unit_price_points`, sets status `refunded`, `ends_at=now()`, and `refund_ledger_id`. It is idempotent (rejects when already refunded) and only applies to `active` purchases.
- `SourcePurchaseExpiryService` runs every `fuoverflow.source.expiry-scan-interval-ms` (default 60s) to flip elapsed `active` purchases to `expired` so stats and access checks stay correct without per-request `ends_at` scans.

Performance (a few hundred concurrent users):

| Index | Table | Purpose |
|-------|-------|---------|
| `ix_source_catalog_active_featured_sort` | `source_catalog_items` | Public featured carousel |
| `ix_source_catalog_active_created` / `_price` / `_views` | `source_catalog_items` | Sort by newest / price / popular |
| `ux_source_catalog_code_live` | `source_catalog_items` | Detail lookup by code |
| `ix_source_purchases_user_status_ends` | `source_purchases` | "My purchases" + stats |
| `ux_source_purchases_idempotency` | `source_purchases` | Idempotent purchase |

Test flow:

1. Admin tops up a user's FUO Point.
2. Admin creates catalog items at `/admin/source/catalog`.
3. User buys at `/suoc/{code}`, views at `/suoc/my-purchases`.
4. Admin reviews and refunds (ADMIN only) at `/admin/source/purchases`.

Validate: `mvn -q -pl source,app -am test`.

## Notes

- PostgreSQL schema lives in `app/src/main/resources/db/migration/V1__init_schema.sql`.
- Database intentionally has no foreign key constraints; validate references in application services through `ReferenceGuard`.
- Files are stored on local server paths configured by `fuoverflow.storage.*`.
