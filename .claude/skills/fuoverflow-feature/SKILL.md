---
name: fuoverflow-feature
description: Use to implement a complete FuOverflow feature from plan to code, including API, service, persistence, migration, tests, and validation.
---

# FuOverflow Feature Implementation Skill

Use this skill when the user asks to build a feature.

## Feature workflow

### 1. Scope the feature

Answer internally before coding:

- Which product feature from `.knowledge/fuoverflow-feature-list.md` is this?
- Which module owns it?
- Which tables already support it?
- Does it need a new migration?
- Does it touch auth/permissions?
- Does it need outbox/notification/search indexing?

### 2. Plan impacted files

Group files by module:

```text
backend/<module>/src/main/java/...
backend/app/src/main/resources/db/migration/...
backend/<module>/src/test/java/...
```

Avoid touching unrelated modules.

### 3. Implement backend slice

Preferred vertical slice order:

1. Migration, if required.
2. Entity/repository.
3. Request/response DTOs.
4. Service/use case.
5. Controller endpoint.
6. Exception/error handling updates.
7. Tests.
8. Documentation/update README when useful.

### 4. API shape

Use `/api/v1` prefix.

Examples:

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/login
GET    /api/v1/forums
GET    /api/v1/forums/{forumSlug}/categories
GET    /api/v1/threads
POST   /api/v1/threads
GET    /api/v1/threads/{threadId}
POST   /api/v1/threads/{threadId}/posts
POST   /api/v1/posts/{postId}/reactions
DELETE /api/v1/posts/{postId}/reactions/{type}
GET    /api/v1/courses
GET    /api/v1/materials
POST   /api/v1/orders
POST   /api/v1/payments/webhooks/<provider>
```

Use REST resources. Auth actions may be action-style.

### 5. Definition of done

A feature is not complete until:

- Request validation exists.
- Service-level business rules exist.
- Persistence code excludes soft-deleted rows where applicable.
- Authorization-sensitive logic uses current authenticated user.
- Errors use standard response model or a clear exception plan.
- Tests or a clear test gap are provided.
- The Maven command for impacted modules has been run or the reason is stated.

## MVP discipline

Build the smallest useful production-shaped feature.

Do not:

- scaffold all tables at once;
- build admin UI/API unless asked;
- invent notification/search side effects unless required;
- add heavy abstractions before two real use cases need them;
- refactor the whole project while implementing one feature.

## Reporting format after work

Return:

```text
Changed files:
- ...

What changed:
- ...

Validation:
- Command: ...
- Result: ...

Notes / remaining:
- ...
```
