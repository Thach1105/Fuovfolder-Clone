# FuOverflow Backend

Base Java Spring Boot monolith backend scaffold.

## Modules

- `app`: Spring Boot entrypoint and runtime configuration.
- `common`: shared config, validation, security, persistence, web primitives.
- `auth`, `user`, `forum`, `thread`, `post`, `reaction`, `notification`, `award`, `coursera`, `membership`, `course`, `material`, `payment`, `search`, `moderation`, `admin`: feature modules.
- `worker`: async worker placeholders for outbox, cache invalidation, counters, notifications, search indexing, file cleanup.

## Local services

```bash
docker compose up -d postgres redis
```

## Build

```bash
mvn clean package
```

## Run

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

## Notes

- PostgreSQL schema lives in `app/src/main/resources/db/migration/V1__init_schema.sql`.
- Database intentionally has no foreign key constraints; validate references in application services through `ReferenceGuard`.
- Files are stored on local server paths configured by `fuoverflow.storage.*`.
