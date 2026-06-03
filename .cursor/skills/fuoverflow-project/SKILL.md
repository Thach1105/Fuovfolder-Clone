---
name: fuoverflow-project
description: Use for any task in the FuOverflow backend project. Loads project architecture, module boundaries, build commands, and non-negotiable decisions.
---

# FuOverflow Project Skill

Use this skill whenever the user asks to implement, refactor, review, debug, or design anything in this repository.

## First actions

1. Read `.claude/CLAUDE.md`.
2. Read relevant files in `.knowledge/`.
3. Inspect the impacted module before proposing changes.
4. Check `backend/app/src/main/resources/db/migration/V1__init_schema.sql` before creating persistence code.

## Project facts

- Java 21.
- Spring Boot 3.5.x.
- Maven multi-module modular monolith.
- Runtime entrypoint: `backend/app`.
- Shared cross-cutting module: `backend/common`.
- PostgreSQL primary database.
- Redis available locally but optional unless the feature explicitly uses it.
- Flyway manages schema.
- Hibernate validates schema only.
- The schema intentionally has no DB foreign keys.
- Cross-reference validation must happen in service/application code.

## Preserve module boundaries

Feature code belongs in its feature module.

Examples:

- Auth endpoints/services/security/session logic -> `backend/auth`.
- User profile/account data -> `backend/user`.
- Forum/category logic -> `backend/forum`.
- Thread list/detail/create/update/lock/pin -> `backend/thread`.
- Post/reply/edit/delete logic -> `backend/post`.
- Reactions/bookmarks -> `backend/reaction` when reaction-specific; bookmarks may live with thread if closer to use case.
- Membership plans/subscriptions/entitlements -> `backend/membership`.
- Courses/lessons/quizzes/enrollments -> `backend/course`.
- Uploaded files/material versions -> `backend/material`.
- Orders/payments/webhooks -> `backend/payment`.
- Outbox/background jobs -> `backend/worker`.
- Shared exceptions, web response wrappers, security primitives, validators -> `backend/common`.

`backend/app` should mostly wire the application, configuration, and migrations. Avoid placing business logic in `app`.

## Implementation discipline

When implementing a feature:

1. Identify tables involved.
2. Identify whether a migration is needed.
3. Add entity/repository only for tables the feature needs now.
4. Add request/response DTOs.
5. Add service logic.
6. Add controller.
7. Add tests where feasible.
8. Run Maven command for impacted modules.

Do not implement unrelated modules just because the database already has tables.

## Build commands

Use the smallest command that validates the change:

```bash
cd backend
mvn -q -pl <module> -am test
mvn -q -pl app -am test
mvn -q -DskipTests package
```

Local services:

```bash
cd backend
docker compose up -d postgres redis
```
