---
name: fuoverflow-database
description: Use for PostgreSQL schema, Flyway migration, JPA entity mapping, repository query, indexes, and database review tasks.
---

# FuOverflow Database Skill

Use this skill before creating or modifying database-related code.

## Current schema principles

- PostgreSQL.
- Flyway migrations live in `backend/app/src/main/resources/db/migration/`.
- Initial schema is `V1__init_schema.sql`.
- No database foreign key constraints by design.
- Relationships are validated in application code.
- UUID primary keys.
- `timestamptz` for timestamps.
- snake_case table and column names.
- soft delete via `deleted_at` where present.

## Existing table groups

Users/auth:

- `users`
- `user_sessions`
- `user_oauth_accounts`
- `roles`
- `role_assignments`

Forum/content:

- `forums`
- `categories`
- `threads`
- `posts`
- `post_reactions`
- `thread_bookmarks`
- `tags`
- `thread_tags`
- `content_flags`
- `moderation_actions`

Learning/materials:

- `courses`
- `course_instructors`
- `course_sections`
- `course_lessons`
- `uploaded_files`
- `materials`
- `material_versions`
- `course_enrollments`
- `lesson_progress`
- `quizzes`
- `quiz_questions`
- `quiz_answers`
- `quiz_attempts`

Commerce/membership:

- `membership_plans`
- `memberships`
- `membership_events`
- `entitlements`
- `orders`
- `order_items`
- `payments`
- `payment_webhook_events`

Gamification/ops:

- `award_definitions`
- `user_awards`
- `points_ledger`
- `certificates`
- `notifications`
- `notification_preferences`
- `search_index_jobs`
- `outbox_events`
- `audit_log`
- `orphan_scan_results`

## Migration rules

When a schema change is needed:

1. Create a new migration, do not edit old migrations after they have been shared/applied.
2. Use a descriptive name: `V2__add_auth_token_rotation_columns.sql`.
3. Keep one logical change per migration.
4. Do not add foreign keys.
5. Add indexes for new lookup patterns.
6. Add check constraints for enum-like status fields.
7. Backfill data safely when adding NOT NULL columns.

## JPA mapping rules

- Prefer UUID reference columns over cross-module entity relationships.
- Avoid `@ManyToOne` across module boundaries.
- Avoid eager loading.
- Use repository query methods or JPQL for simple queries.
- Use native SQL only when PostgreSQL-specific features are necessary.
- Keep entity persistence concerns separate from API DTOs.

## Reference validation

Before writing a record with reference IDs, validate required references in service layer.

Examples:

```java
referenceGuard.requireActiveUser(userId);
referenceGuard.requireForumExists(forumId);
referenceGuard.requireCategoryExists(categoryId);
referenceGuard.requireThreadOpen(threadId);
```

If `ReferenceGuard` is not yet implemented for a new table, add a focused implementation in the owning module or common layer instead of adding DB foreign keys.

## Query/index checklist

For every list endpoint, verify:

- filter columns are indexed;
- sort columns are indexed or acceptable for MVP scale;
- pagination is deterministic;
- soft-deleted rows are excluded where relevant;
- visibility/status constraints are respected.

## Do not do these

- Do not change `ddl-auto` from `validate`.
- Do not add foreign keys casually.
- Do not drop columns/tables in MVP migrations unless requested.
- Do not use reserved words for table/column names.
- Do not store raw tokens or secret values.
