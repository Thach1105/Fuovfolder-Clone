# FuOverflow Backend

Base Java Spring Boot monolith backend scaffold.

## Modules

- `app`: Spring Boot entrypoint and runtime configuration.
- `common`: shared config, validation, security, persistence, web primitives.
- `auth`, `user`, `forum`, `thread`, `post`, `reaction`, `notification`, `award`, `membership`, `course`, `material`, `payment`, `search`, `moderation`, `admin`: feature modules.
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

## Notes

- PostgreSQL schema lives in `app/src/main/resources/db/migration/V1__init_schema.sql`.
- Database intentionally has no foreign key constraints; validate references in application services through `ReferenceGuard`.
- Files are stored on local server paths configured by `fuoverflow.storage.*`.
