# Timestamp Timezone Standardization — Design Spec

**Date:** 2026-06-28
**Status:** Approved
**Scope:** Global — all API responses across all modules

---

## 1. Problem

All API response timestamps are serialized as UTC (`"2026-06-28T07:30:00Z"`) by Jackson's default JSR-310 behavior. The project requires timestamps in API responses to display in `Asia/Ho_Chi_Minh` timezone (`+07:00`).

---

## 2. Current State

- All DTO timestamp fields use `java.time.Instant` consistently across all modules.
- No custom Jackson config exists — Spring Boot auto-configures JSR-310 with UTC output.
- DB stores `timestamptz` (UTC internally via PostgreSQL standard).
- JPA layer configured with `hibernate.jdbc.time_zone: UTC`.

---

## 3. Solution

Add a single `JacksonConfig` bean in `common` module that registers a custom `Instant` serializer. Because all modules share the same Spring Boot application context, this one change applies globally.

**Internal code stays as `Instant` throughout** — no changes to entities, services, repositories, or DTO field types. Only the serialization output changes.

---

## 4. Design

### Output Format

Before:
```json
"createdAt": "2026-06-28T07:30:00.123456Z"
```

After:
```json
"createdAt": "2026-06-28T14:30:00.123456+07:00"
```

Format: ISO-8601 with offset (`DateTimeFormatter.ISO_OFFSET_DATE_TIME`).

### Implementation

**File:** `backend/common/src/main/java/com/fuoverflow/common/config/JacksonConfig.java`

```java
@Configuration
public class JacksonConfig {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer timestampTimezoneCustomizer() {
        return builder -> builder.serializerByType(Instant.class, new InstantVnSerializer());
    }

    private static class InstantVnSerializer extends JsonSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            gen.writeString(value.atZone(VN_ZONE)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }
}
```

---

## 5. Files Changed

| File | Change |
|------|--------|
| `common/src/main/java/com/fuoverflow/common/config/JacksonConfig.java` | New file |

---

## 6. What Does NOT Change

- DB schema and `timestamptz` columns — still store UTC
- `hibernate.jdbc.time_zone: UTC` — unchanged
- All entity fields (`Instant`) — unchanged
- All DTO fields (`Instant`) — unchanged
- All service/repository logic — unchanged
- Deserialization (incoming request timestamps) — unchanged, still parses ISO-8601

---

## 7. Trade-offs & Decisions

| Decision | Rationale |
|----------|-----------|
| `Jackson2ObjectMapperBuilderCustomizer` over `@Bean ObjectMapper` | Composes with Spring Boot auto-config; avoids replacing the entire mapper |
| Serialize only, not deserialize | Incoming timestamps from clients parse fine as ISO-8601 regardless of offset |
| `ISO_OFFSET_DATE_TIME` format | Unambiguous, includes offset, widely supported by clients |
| `Instant` internal type unchanged | Avoids ripple changes across all modules; conversion is only a presentation concern |

---

## 8. Out of Scope

- Changing `LocalDateTime` fields (none exist in current DTOs)
- Per-user timezone preferences
- DB session timezone changes
