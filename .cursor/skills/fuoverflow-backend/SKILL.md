---
name: fuoverflow-backend
description: Use when writing or reviewing Java 21 Spring Boot 3 backend code for FuOverflow modules.
---

# FuOverflow Backend Spring Skill

Apply this skill when writing backend code.

## Layering rules

Controller:

- Defines REST endpoint paths.
- Accepts `@Valid` request DTOs.
- Reads auth principal/current user when needed.
- Delegates to service.
- Returns response DTOs.
- Does not contain business rules, entity mutation rules, or repository calls.

Service/application layer:

- Owns use cases and transactions.
- Validates references with `ReferenceGuard` or module-specific guard methods.
- Checks authorization-sensitive business rules beyond route-level security.
- Updates counters and emits outbox events when required.

Repository/persistence:

- Uses Spring Data JPA repositories for normal CRUD/query paths.
- Keeps native SQL minimal and justified.
- Never returns JPA entities directly to controllers.

Common module:

- Shared exception hierarchy.
- Shared response/error DTOs.
- Shared validation primitives.
- Shared security principal annotations/utilities.
- Shared persistence base types only when they truly reduce duplication.

## Package convention

For real feature code, use:

```text
com.fuoverflow.<module>.api
com.fuoverflow.<module>.api.dto
com.fuoverflow.<module>.application
com.fuoverflow.<module>.domain
com.fuoverflow.<module>.persistence
com.fuoverflow.<module>.config
com.fuoverflow.<module>.support
```

Do not create `controller`, `service`, `repository` package names if the module already uses the above convention for a feature. Prefer consistency.

## Entity rules

- Use UUID for IDs.
- Keep entity names singular: `UserEntity`, `ThreadEntity`.
- Map table/column names explicitly when helpful.
- Avoid eager relationships. Because DB has no FK constraints, avoid deep `@ManyToOne` graphs unless there is a strong reason.
- Prefer storing reference IDs (`UUID userId`) over JPA relationships for cross-module references.
- Use optimistic locking only where the schema has `lock_version`.
- Respect soft delete columns.

## DTO rules

- Request DTOs use validation annotations.
- Response DTOs expose only client-needed fields.
- Never expose password hashes, token hashes, internal audit details, payment webhook secrets, or private storage paths.

## Error handling rules

Before creating many custom exceptions, define or reuse common exceptions in `backend/common`:

- `BadRequestException`
- `UnauthorizedException`
- `ForbiddenException`
- `NotFoundException`
- `ConflictException`
- `ValidationErrorResponse`
- global `@RestControllerAdvice`

Use domain-specific messages, but do not leak sensitive security details.

## Transaction rules

- Use `@Transactional` on service methods that mutate state.
- Use `@Transactional(readOnly = true)` for read paths.
- Avoid transaction boundaries in controllers.
- For async side effects, prefer outbox table over direct best-effort execution when reliability matters.

## Pagination and sorting

For list endpoints:

- Use page/size or cursor depending on use case.
- Cap max `size`.
- Stable sorting is required for cursor pagination.
- Avoid unindexed sort/filter paths.

## Code smell checklist

Reject or revise if code does any of these:

- Business logic in controller.
- Entity returned directly from controller.
- DB foreign key added casually.
- `ddl-auto=update` introduced.
- Token logged.
- `localStorage` recommended for browser token storage.
- New infrastructure introduced without request.
- Large unrelated refactor mixed with feature.
