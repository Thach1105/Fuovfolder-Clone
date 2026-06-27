# Timestamp Timezone Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all API response timestamps render in `Asia/Ho_Chi_Minh` timezone (`+07:00`) instead of UTC `Z`.

**Architecture:** Register a single custom `JsonSerializer<Instant>` via `Jackson2ObjectMapperBuilderCustomizer` in `common` module. All modules share the same Spring Boot application context so one bean covers the whole app. No DTO or entity changes needed.

**Tech Stack:** Spring Boot 3.5.x, Jackson 2.17.x (JSR-310), `java.time.ZoneId`, `DateTimeFormatter.ISO_OFFSET_DATE_TIME`.

## Global Constraints

- Java 21, Spring Boot 3.5.x
- No new Maven dependencies (Jackson JSR-310 already on classpath via Spring Boot)
- Internal code stays as `java.time.Instant` throughout — no field type changes
- DB and JPA layer stay UTC (`hibernate.jdbc.time_zone: UTC` unchanged)
- Output format: ISO-8601 with offset, e.g. `"2026-06-28T14:30:00.123456+07:00"`

---

### Task 1: Add `JacksonConfig` with `Asia/Ho_Chi_Minh` `Instant` serializer

**Files:**
- Create: `backend/common/src/main/java/com/fuoverflow/common/config/JacksonConfig.java`
- Create (test): `backend/common/src/test/java/com/fuoverflow/common/config/JacksonConfigTest.java`

**Interfaces:**
- Produces: Spring Boot `Jackson2ObjectMapperBuilderCustomizer` bean named implicitly — consumed by Spring Boot auto-config

- [ ] **Step 1: Write failing test**

Create `backend/common/src/test/java/com/fuoverflow/common/config/JacksonConfigTest.java`:

```java
package com.fuoverflow.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {JacksonAutoConfiguration.class, JacksonConfig.class})
class JacksonConfigTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void instantShouldSerializeWithVietnamOffset() throws Exception {
        // 2026-06-28T00:00:00Z = 2026-06-28T07:00:00+07:00
        Instant instant = Instant.parse("2026-06-28T00:00:00Z");
        String json = objectMapper.writeValueAsString(instant);
        assertThat(json).contains("+07:00");
        assertThat(json).doesNotContain("Z\"");
    }

    @Test
    void instantShouldPreserveCorrectTime() throws Exception {
        Instant instant = Instant.parse("2026-06-28T00:00:00Z");
        String json = objectMapper.writeValueAsString(instant);
        // UTC midnight = 07:00 Vietnam time
        assertThat(json).contains("07:00:00");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend && mvn -q test -pl common -Dtest=JacksonConfigTest
```

Expected: FAIL — no `JacksonConfig` class exists yet, or `Instant` serializes as `Z` not `+07:00`.

- [ ] **Step 3: Create `JacksonConfig`**

Create `backend/common/src/main/java/com/fuoverflow/common/config/JacksonConfig.java`:

```java
package com.fuoverflow.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer timestampTimezoneCustomizer() {
        return builder -> builder.serializerByType(Instant.class, new InstantVnSerializer());
    }

    private static class InstantVnSerializer extends JsonSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.atZone(VN_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
cd backend && mvn -q test -pl common -Dtest=JacksonConfigTest
```

Expected: PASS.

- [ ] **Step 5: Build full project**

```bash
cd backend && mvn -q -DskipTests package
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Smoke test serialization end-to-end**

Start the app locally (requires Postgres + Redis running):

```bash
cd backend && docker compose up -d postgres redis
mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local
```

Call any endpoint that returns a timestamp and verify `+07:00`:

```bash
curl -s http://localhost:8080/actuator/health | jq .
# Then call an authenticated endpoint or register a user and check timestamp fields
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test1234!","displayName":"Test User","username":"testuser"}' | jq .
```

Expected: `timestamp` field in response contains `+07:00`, e.g. `"2026-06-28T14:30:00.123+07:00"`.

- [ ] **Step 7: Commit**

```bash
git add backend/common/src
git commit -m "feat(common): serialize Instant as Asia/Ho_Chi_Minh timezone in API responses"
```
