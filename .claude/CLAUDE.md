# FuOverflow Project Instructions

## Project identity

This repository is a FuOverflow-like community backend. It is a Java 21, Spring Boot 3.5.x, Maven multi-module monolith.

Primary modules:

- `backend/app`: Spring Boot entrypoint, runtime config, Flyway migrations.
- `backend/common`: shared config, validation, security, persistence, web primitives.
- Feature modules: `auth`, `user`, `forum`, `thread`, `post`, `reaction`, `notification`, `award`, `membership`, `course`, `material`, `payment`, `search`, `moderation`, `admin`.
- `backend/worker`: async worker placeholders for outbox, cache invalidation, counters, notifications, search indexing, file cleanup.

Important knowledge files:

- `.knowledge/fuoverflow-feature-list.md`: product scope and public feature list.
- `.knowledge/system-architecture-design.md`: architecture decisions.
- `.knowledge/database-schema.sql`: database reference.
- `.knowledge/auth-module-plan.md`: auth/JWT/session design.
- `.knowledge/user-module-plan.md`: user module design.

Read the relevant knowledge file before implementing a feature.

## Non-negotiable architecture decisions

- Keep this as a modular monolith unless explicitly asked otherwise.
- Do not introduce microservices, gateway, Kafka, Elasticsearch, MinIO, S3, or new infrastructure unless requested.
- PostgreSQL is the source of truth.
- Redis is optional for cache/token state and must not be required for basic local startup unless explicitly implemented.
- Flyway migrations are the only way to change database schema.
- Hibernate `ddl-auto` must remain `validate`, not `update` or `create`.
- The database intentionally has no foreign key constraints. Do not add `FOREIGN KEY` constraints unless the user explicitly reverses this decision.
- Validate cross-table references in application services via `ReferenceGuard` or module-level guard services.
- Use soft delete where the schema has `deleted_at`.
- Do not store secrets, raw refresh tokens, passwords, API keys, or private keys in git.

## Coding workflow

Before editing code:

1. Read the current module files and related `.knowledge` docs.
2. Identify impacted modules.
3. Check existing schema tables and enum/check constraint values.
4. Produce a short implementation plan.
5. Keep changes focused on the requested feature.

After editing code:

1. Run the smallest useful Maven command.
2. Report changed files.
3. Explain schema/API/security changes.
4. Mention anything not completed.

Useful commands:

```bash
cd backend
mvn -q -DskipTests package
mvn -q test
mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local
mvn -q -pl app -am test
```

Local dependencies:

```bash
cd backend
docker compose up -d postgres redis
```

Health check:

```text
GET http://localhost:8080/actuator/health
```

## Java/Spring standards

- Java 21.
- Spring Boot 3.5.x.
- Use constructor injection.
- Prefer records for immutable request/response DTOs.
- Use `jakarta.validation` annotations on request DTOs.
- Do not put business logic in controllers.
- Controllers only validate input, delegate to service, and map response.
- Services own business logic and reference validation.
- Repositories only access persistence.
- Avoid static util classes unless truly stateless and shared.
- Avoid over-engineering generic abstractions in MVP.

Recommended package layout inside a feature module:

```text
com.fuoverflow.<module>/
  api/
    <Module>Controller.java
    dto/
  application/
    <Module>Service.java
  domain/
  persistence/
    <Entity>.java
    <Repository>.java
  config/
  support/
```

## API standards

- Version APIs under `/api/v1/...` unless project convention changes.
- Use clear REST resources, not RPC-style endpoints, except auth actions where appropriate.
- Return explicit response DTOs, not JPA entities.
- Validate input with `@Valid`.
- Standardize error response from common web/exception package before adding many controllers.
- Do not leak stack traces or sensitive auth details to clients.

## Database standards

- ID type: `uuid`.
- Time type: `timestamptz`.
- Keep table/column names snake_case.
- Use partial unique indexes for live soft-deleted data when needed.
- Respect existing check constraints and enum values in `V1__init_schema.sql`.
- Add indexes for common lookup/sort paths.
- Do not create DB foreign keys.
- Prefer one migration per logical change, named like `V2__add_auth_token_rotation.sql`.

## Security standards

For browser web app auth:

- Prefer HttpOnly, Secure, SameSite cookies for refresh token/session storage.
- Do not store JWT in `localStorage`.
- Access token should be short-lived.
- Refresh token should be opaque, random, rotating, and stored server-side as a hash only.
- RS256 is preferred over HS256 when multiple services/gateways may verify tokens.
- Never log raw tokens or password hashes.
- Password hashing must use a strong adaptive hash such as BCrypt/Argon2 via Spring Security password encoders.

## Testing standards

- Add tests for service-level business rules when implementing real logic.
- Add MVC/security tests for controller behavior when endpoints are introduced.
- Use integration tests sparingly for DB/migration-sensitive logic.
- Do not ignore failing tests without explaining why.

## Response style when working in Claude Code

- Be direct and implementation-focused.
- Show files changed and commands run.
- When uncertain, inspect the codebase rather than guessing.
- Never silently change architecture decisions.
