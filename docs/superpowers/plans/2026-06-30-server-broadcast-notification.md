# Server-Wide Broadcast Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Redis Pub/Sub + SSE broadcast system that pushes real-time ticker messages (marquee) to all connected clients. First use case: admin configures a VND threshold → deposits above it auto-broadcast a ticker message server-wide.

**Architecture:** Common module defines a `BroadcastPublisher` interface + Redis Pub/Sub infra. A new `broadcast` module owns the SSE endpoint, Redis listener, emitter pool, and admin config API. Payment module publishes a broadcast event after crediting points. Frontend subscribes via `EventSource` and displays a scrolling ticker.

**Tech Stack:** Spring Data Redis (Lettuce), `SseEmitter`, `ApplicationEventPublisher`, Redis Pub/Sub, Flyway migration, PostgreSQL `jsonb`.

## Global Constraints

- Java 21, Spring Boot 3.5.x, Maven multi-module monolith.
- No foreign keys. Soft delete where schema has `deleted_at`.
- Flyway only for schema changes. Hibernate `ddl-auto=validate`.
- Redis already running (Spring Data Redis + Lettuce configured).
- Latest migration: `V9__source_indexes.sql` → next is `V10`.
- API prefix: `/api/v1/...`.
- SSE stream endpoint must allow authenticated users (JWT cookie).
- Admin endpoints use `@RequirePermission`.
- Standard response envelope: `ApiResponse<T>`.

---

### Task 1: Flyway Migration — `broadcast_configs` + `broadcast_events` Tables

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V10__broadcast_system.sql`

**Interfaces:**
- Consumes: nothing
- Produces: `broadcast_configs` table (admin-managed rules), `broadcast_events` table (audit log of sent broadcasts)

- [ ] **Step 1: Write the migration**

```sql
-- Broadcast configuration rules (admin-managed)
CREATE TABLE broadcast_configs (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  varchar(64)  NOT NULL,
    config_json jsonb        NOT NULL DEFAULT '{}',
    enabled     boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_broadcast_configs_event_type
    ON broadcast_configs(event_type);

-- Audit log of broadcast events sent
CREATE TABLE broadcast_events (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  varchar(64)  NOT NULL,
    message     text         NOT NULL,
    data_json   jsonb        NOT NULL DEFAULT '{}',
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX ix_broadcast_events_type_created
    ON broadcast_events(event_type, created_at DESC);
```

- [ ] **Step 2: Validate migration compiles**

Run: `cd backend && mvn -q -pl app flyway:validate -Dflyway.validateMigrationNaming=true 2>&1 || echo "expected — DB not running"`

Just verify the file is syntactically valid SQL. Full validation happens at app startup.

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V10__broadcast_system.sql
git commit -m "feat(broadcast): add broadcast_configs and broadcast_events tables"
```

---

### Task 2: Common Module — `BroadcastPublisher` Interface + `BroadcastMessage` Record

**Files:**
- Create: `backend/common/src/main/java/com/fuoverflow/common/broadcast/BroadcastPublisher.java`
- Create: `backend/common/src/main/java/com/fuoverflow/common/broadcast/BroadcastMessage.java`

**Interfaces:**
- Consumes: nothing
- Produces: `BroadcastPublisher.publish(BroadcastMessage)` — called by any module to broadcast. `BroadcastMessage(String eventType, String message, Map<String, Object> data)` — the payload sent over SSE.

- [ ] **Step 1: Create `BroadcastMessage` record**

```java
package com.fuoverflow.common.broadcast;

import java.time.Instant;
import java.util.Map;

public record BroadcastMessage(
        String eventType,
        String message,
        Map<String, Object> data,
        Instant timestamp
) {
    public BroadcastMessage(String eventType, String message, Map<String, Object> data) {
        this(eventType, message, data, Instant.now());
    }
}
```

- [ ] **Step 2: Create `BroadcastPublisher` interface**

```java
package com.fuoverflow.common.broadcast;

public interface BroadcastPublisher {
    void publish(BroadcastMessage message);
}
```

- [ ] **Step 3: Compile**

Run: `cd backend && mvn -q -pl common compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/common/src/main/java/com/fuoverflow/common/broadcast/
git commit -m "feat(common): add BroadcastPublisher interface and BroadcastMessage record"
```

---

### Task 3: Broadcast Module — Maven Module + Entity + Repository

**Files:**
- Create: `backend/broadcast/pom.xml`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/BroadcastModule.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/persistence/BroadcastConfigEntity.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/persistence/BroadcastConfigRepository.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/persistence/BroadcastEventEntity.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/persistence/BroadcastEventRepository.java`
- Modify: `backend/pom.xml` — add `<module>broadcast</module>`
- Modify: `backend/app/pom.xml` — add `fuoverflow-broadcast` dependency

**Interfaces:**
- Consumes: Tables from Task 1
- Produces: JPA entities and repositories for `broadcast_configs` and `broadcast_events`

- [ ] **Step 1: Create `backend/broadcast/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.fuoverflow</groupId>
        <artifactId>fuoverflow-backend</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>fuoverflow-broadcast</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.fuoverflow</groupId>
            <artifactId>fuoverflow-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Add module to parent `pom.xml`**

Add `<module>broadcast</module>` to the `<modules>` section in `backend/pom.xml`.

- [ ] **Step 3: Add dependency in `backend/app/pom.xml`**

```xml
<dependency>
    <groupId>com.fuoverflow</groupId>
    <artifactId>fuoverflow-broadcast</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 4: Create module marker**

```java
package com.fuoverflow.broadcast;

public final class BroadcastModule {
    private BroadcastModule() {}
}
```

- [ ] **Step 5: Create `BroadcastConfigEntity`**

```java
package com.fuoverflow.broadcast.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "broadcast_configs")
public class BroadcastConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, unique = true, length = 64)
    private String eventType;

    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private String configJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BroadcastConfigEntity() {}

    public static BroadcastConfigEntity create(String eventType, String configJson) {
        BroadcastConfigEntity e = new BroadcastConfigEntity();
        e.eventType = eventType;
        e.configJson = configJson;
        e.enabled = true;
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getConfigJson() { return configJson; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateConfig(String configJson, boolean enabled) {
        this.configJson = configJson;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
```

- [ ] **Step 6: Create `BroadcastConfigRepository`**

```java
package com.fuoverflow.broadcast.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BroadcastConfigRepository extends JpaRepository<BroadcastConfigEntity, UUID> {
    Optional<BroadcastConfigEntity> findByEventType(String eventType);
}
```

- [ ] **Step 7: Create `BroadcastEventEntity`**

```java
package com.fuoverflow.broadcast.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "broadcast_events")
public class BroadcastEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "data_json", nullable = false, columnDefinition = "jsonb")
    private String dataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BroadcastEventEntity() {}

    public static BroadcastEventEntity create(String eventType, String message, String dataJson) {
        BroadcastEventEntity e = new BroadcastEventEntity();
        e.eventType = eventType;
        e.message = message;
        e.dataJson = dataJson;
        e.createdAt = Instant.now();
        return e;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public String getDataJson() { return dataJson; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 8: Create `BroadcastEventRepository`**

```java
package com.fuoverflow.broadcast.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BroadcastEventRepository extends JpaRepository<BroadcastEventEntity, UUID> {
}
```

- [ ] **Step 9: Compile**

Run: `cd backend && mvn -q -pl broadcast -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add backend/broadcast/ backend/pom.xml backend/app/pom.xml
git commit -m "feat(broadcast): add broadcast module with entities and repositories"
```

---

### Task 4: Redis Pub/Sub Infrastructure — Publisher + Listener + Emitter Pool

**Files:**
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/infra/RedisBroadcastPublisher.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/infra/RedisBroadcastListener.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/infra/BroadcastEmitterPool.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/config/BroadcastRedisConfig.java`

**Interfaces:**
- Consumes: `BroadcastPublisher` (Task 2), `BroadcastMessage` (Task 2)
- Produces: `RedisBroadcastPublisher` implements `BroadcastPublisher` — serializes message to JSON + publishes to Redis channel `broadcast:all`. `RedisBroadcastListener` — subscribes to `broadcast:all`, deserializes, pushes to `BroadcastEmitterPool`. `BroadcastEmitterPool.register(SseEmitter): void`, `BroadcastEmitterPool.broadcast(BroadcastMessage): void`.

- [ ] **Step 1: Create `BroadcastEmitterPool`**

```java
package com.fuoverflow.broadcast.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class BroadcastEmitterPool {

    private static final Logger log = LoggerFactory.getLogger(BroadcastEmitterPool.class);
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public BroadcastEmitterPool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
    }

    public void broadcast(BroadcastMessage message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("Failed to serialize broadcast message", e);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(message.eventType())
                        .data(json));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    }

    public int activeCount() {
        return emitters.size();
    }
}
```

- [ ] **Step 2: Create `RedisBroadcastPublisher`**

```java
package com.fuoverflow.broadcast.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import com.fuoverflow.common.broadcast.BroadcastPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisBroadcastPublisher implements BroadcastPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisBroadcastPublisher.class);
    static final String CHANNEL = "broadcast:all";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisBroadcastPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(BroadcastMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHANNEL, json);
            log.debug("Published broadcast: type={}", message.eventType());
        } catch (Exception e) {
            log.error("Failed to publish broadcast: type={}", message.eventType(), e);
        }
    }
}
```

- [ ] **Step 3: Create `RedisBroadcastListener`**

```java
package com.fuoverflow.broadcast.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class RedisBroadcastListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisBroadcastListener.class);
    private final BroadcastEmitterPool emitterPool;
    private final ObjectMapper objectMapper;

    public RedisBroadcastListener(BroadcastEmitterPool emitterPool, ObjectMapper objectMapper) {
        this.emitterPool = emitterPool;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            BroadcastMessage broadcast = objectMapper.readValue(message.getBody(), BroadcastMessage.class);
            emitterPool.broadcast(broadcast);
        } catch (Exception e) {
            log.error("Failed to process broadcast message from Redis", e);
        }
    }
}
```

- [ ] **Step 4: Create `BroadcastRedisConfig`**

```java
package com.fuoverflow.broadcast.config;

import com.fuoverflow.broadcast.infra.RedisBroadcastListener;
import com.fuoverflow.broadcast.infra.RedisBroadcastPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class BroadcastRedisConfig {

    @Bean
    RedisMessageListenerContainer broadcastListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisBroadcastListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(RedisBroadcastPublisher.CHANNEL));
        return container;
    }
}
```

- [ ] **Step 5: Compile**

Run: `cd backend && mvn -q -pl broadcast -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/broadcast/src/main/java/com/fuoverflow/broadcast/infra/ backend/broadcast/src/main/java/com/fuoverflow/broadcast/config/
git commit -m "feat(broadcast): Redis Pub/Sub publisher, listener, and SSE emitter pool"
```

---

### Task 5: Broadcast Service + SSE Controller

**Files:**
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/BroadcastService.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/BroadcastController.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java` — permit SSE endpoint for GET

**Interfaces:**
- Consumes: `BroadcastEmitterPool` (Task 4), `BroadcastEventRepository` (Task 3), `BroadcastConfigRepository` (Task 3)
- Produces: `GET /api/v1/broadcasts/stream` — SSE endpoint returning `SseEmitter`. `BroadcastService.logEvent(BroadcastMessage): void` — persists broadcast to audit table. `BroadcastService.evaluateAndBroadcast(String eventType, Map<String, Object> eventData): void` — checks config, builds message, publishes.

- [ ] **Step 1: Create `BroadcastService`**

```java
package com.fuoverflow.broadcast.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.broadcast.persistence.BroadcastConfigEntity;
import com.fuoverflow.broadcast.persistence.BroadcastConfigRepository;
import com.fuoverflow.broadcast.persistence.BroadcastEventEntity;
import com.fuoverflow.broadcast.persistence.BroadcastEventRepository;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import com.fuoverflow.common.broadcast.BroadcastPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class BroadcastService {

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);
    private final BroadcastConfigRepository configRepo;
    private final BroadcastEventRepository eventRepo;
    private final BroadcastPublisher publisher;
    private final ObjectMapper objectMapper;

    public BroadcastService(BroadcastConfigRepository configRepo,
                            BroadcastEventRepository eventRepo,
                            BroadcastPublisher publisher,
                            ObjectMapper objectMapper) {
        this.configRepo = configRepo;
        this.eventRepo = eventRepo;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void evaluateAndBroadcast(String eventType, Map<String, Object> eventData) {
        BroadcastConfigEntity config = configRepo.findByEventType(eventType).orElse(null);
        if (config == null || !config.isEnabled()) {
            return;
        }

        Map<String, Object> configMap = parseJson(config.getConfigJson());
        BroadcastEvaluator evaluator = BroadcastEvaluatorFactory.get(eventType);
        if (evaluator == null) {
            log.warn("No evaluator registered for event type: {}", eventType);
            return;
        }

        String message = evaluator.evaluate(configMap, eventData);
        if (message == null) {
            return;
        }

        BroadcastMessage broadcast = new BroadcastMessage(eventType, message, eventData);
        publisher.publish(broadcast);
        logEvent(broadcast);
    }

    @Transactional
    public void logEvent(BroadcastMessage broadcast) {
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(broadcast.data());
        } catch (Exception e) {
            dataJson = "{}";
        }
        BroadcastEventEntity event = BroadcastEventEntity.create(
                broadcast.eventType(), broadcast.message(), dataJson);
        eventRepo.save(event);
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
```

- [ ] **Step 2: Create `BroadcastEvaluator` interface**

```java
package com.fuoverflow.broadcast.application;

import java.util.Map;

public interface BroadcastEvaluator {
    String evaluate(Map<String, Object> config, Map<String, Object> eventData);
}
```

- [ ] **Step 3: Create `BroadcastEvaluatorFactory`**

```java
package com.fuoverflow.broadcast.application;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BroadcastEvaluatorFactory {

    private static final Map<String, BroadcastEvaluator> EVALUATORS = new ConcurrentHashMap<>();

    private BroadcastEvaluatorFactory() {}

    public static void register(String eventType, BroadcastEvaluator evaluator) {
        EVALUATORS.put(eventType, evaluator);
    }

    public static BroadcastEvaluator get(String eventType) {
        return EVALUATORS.get(eventType);
    }
}
```

- [ ] **Step 4: Create `DepositBroadcastEvaluator`**

```java
package com.fuoverflow.broadcast.application;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Component
public class DepositBroadcastEvaluator implements BroadcastEvaluator {

    static final String EVENT_TYPE = "deposit.completed";

    @PostConstruct
    void register() {
        BroadcastEvaluatorFactory.register(EVENT_TYPE, this);
    }

    @Override
    public String evaluate(Map<String, Object> config, Map<String, Object> eventData) {
        long thresholdVnd = ((Number) config.getOrDefault("thresholdVnd", 0)).longValue();
        long amountVnd = ((Number) eventData.getOrDefault("amountVnd", 0)).longValue();

        if (amountVnd < thresholdVnd) {
            return null;
        }

        String displayName = (String) eventData.getOrDefault("displayName", "Một thành viên");
        String template = (String) config.getOrDefault("messageTemplate",
                "%s vừa nạp %,d VND vào tài khoản!");
        return String.format(template, displayName, amountVnd);
    }
}
```

- [ ] **Step 5: Create `BroadcastController`**

```java
package com.fuoverflow.broadcast.api;

import com.fuoverflow.broadcast.infra.BroadcastEmitterPool;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/broadcasts")
public class BroadcastController {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final BroadcastEmitterPool emitterPool;

    public BroadcastController(BroadcastEmitterPool emitterPool) {
        this.emitterPool = emitterPool;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitterPool.register(emitter);
        return emitter;
    }
}
```

- [ ] **Step 6: Permit SSE endpoint in SecurityConfig**

In `SecurityConfig.java`, add to the `permitAll()` GET matchers:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/broadcasts/stream")
.permitAll()
```

Add this after the existing `.requestMatchers(HttpMethod.GET, "/api/v1/membership/plans").permitAll()` line, before `.anyRequest().authenticated()`.

**Note:** SSE endpoint is public so unauthenticated visitors can also see the ticker. This is a display-only feature showing public celebratory messages. If auth is required later, remove this line — the default `.anyRequest().authenticated()` will enforce it.

- [ ] **Step 7: Compile**

Run: `cd backend && mvn -q -pl broadcast -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/ \
       backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/ \
       backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java
git commit -m "feat(broadcast): BroadcastService with evaluator pattern + SSE controller"
```

---

### Task 6: Admin Broadcast Config API

**Files:**
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/BroadcastAdminController.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/BroadcastConfigRequest.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/BroadcastConfigResponse.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/BroadcastConfigService.java`

**Interfaces:**
- Consumes: `BroadcastConfigRepository` (Task 3), `@RequirePermission` (common security)
- Produces: Admin REST API for managing broadcast configs:
  - `GET /api/v1/admin/broadcasts/configs` — list all configs
  - `GET /api/v1/admin/broadcasts/configs/{eventType}` — get one config
  - `PUT /api/v1/admin/broadcasts/configs/{eventType}` — create or update config
  - `PATCH /api/v1/admin/broadcasts/configs/{eventType}/toggle` — enable/disable

- [ ] **Step 1: Create `BroadcastConfigRequest`**

```java
package com.fuoverflow.broadcast.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record BroadcastConfigRequest(
        @NotNull Map<String, Object> config,
        boolean enabled
) {}
```

- [ ] **Step 2: Create `BroadcastConfigResponse`**

```java
package com.fuoverflow.broadcast.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BroadcastConfigResponse(
        UUID id,
        String eventType,
        Map<String, Object> config,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}
```

- [ ] **Step 3: Create `BroadcastConfigService`**

```java
package com.fuoverflow.broadcast.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.broadcast.api.dto.BroadcastConfigRequest;
import com.fuoverflow.broadcast.api.dto.BroadcastConfigResponse;
import com.fuoverflow.broadcast.persistence.BroadcastConfigEntity;
import com.fuoverflow.broadcast.persistence.BroadcastConfigRepository;
import com.fuoverflow.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class BroadcastConfigService {

    private final BroadcastConfigRepository configRepo;
    private final ObjectMapper objectMapper;

    public BroadcastConfigService(BroadcastConfigRepository configRepo,
                                  ObjectMapper objectMapper) {
        this.configRepo = configRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<BroadcastConfigResponse> listAll() {
        return configRepo.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BroadcastConfigResponse getByEventType(String eventType) {
        return toResponse(findByEventType(eventType));
    }

    @Transactional
    public BroadcastConfigResponse upsert(String eventType, BroadcastConfigRequest request) {
        String configJson = toJson(request.config());
        BroadcastConfigEntity entity = configRepo.findByEventType(eventType)
                .map(existing -> {
                    existing.updateConfig(configJson, request.enabled());
                    return existing;
                })
                .orElseGet(() -> {
                    BroadcastConfigEntity created = BroadcastConfigEntity.create(eventType, configJson);
                    if (!request.enabled()) {
                        created.updateConfig(configJson, false);
                    }
                    return created;
                });
        return toResponse(configRepo.save(entity));
    }

    @Transactional
    public BroadcastConfigResponse toggle(String eventType) {
        BroadcastConfigEntity entity = findByEventType(eventType);
        entity.updateConfig(entity.getConfigJson(), !entity.isEnabled());
        return toResponse(configRepo.save(entity));
    }

    private BroadcastConfigEntity findByEventType(String eventType) {
        return configRepo.findByEventType(eventType)
                .orElseThrow(() -> new NotFoundException(
                        "BROADCAST_CONFIG_NOT_FOUND",
                        "Broadcast config not found: " + eventType));
    }

    private BroadcastConfigResponse toResponse(BroadcastConfigEntity entity) {
        Map<String, Object> configMap;
        try {
            configMap = objectMapper.readValue(entity.getConfigJson(), new TypeReference<>() {});
        } catch (Exception e) {
            configMap = Map.of();
        }
        return new BroadcastConfigResponse(
                entity.getId(),
                entity.getEventType(),
                configMap,
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
```

- [ ] **Step 4: Create `BroadcastAdminController`**

```java
package com.fuoverflow.broadcast.api;

import com.fuoverflow.broadcast.api.dto.BroadcastConfigRequest;
import com.fuoverflow.broadcast.api.dto.BroadcastConfigResponse;
import com.fuoverflow.broadcast.application.BroadcastConfigService;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/broadcasts/configs")
@RequirePermission("admin.panel:access")
public class BroadcastAdminController {

    private final BroadcastConfigService configService;

    public BroadcastAdminController(BroadcastConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    @RequirePermission("broadcast.admin:read")
    public ApiResponse<List<BroadcastConfigResponse>> list() {
        return ApiResponse.ok(configService.listAll());
    }

    @GetMapping("/{eventType}")
    @RequirePermission("broadcast.admin:read")
    public ApiResponse<BroadcastConfigResponse> get(@PathVariable String eventType) {
        return ApiResponse.ok(configService.getByEventType(eventType));
    }

    @PutMapping("/{eventType}")
    @RequirePermission("broadcast.admin:update")
    public ApiResponse<BroadcastConfigResponse> upsert(
            @PathVariable String eventType,
            @Valid @RequestBody BroadcastConfigRequest request) {
        return ApiResponse.ok(configService.upsert(eventType, request));
    }

    @PatchMapping("/{eventType}/toggle")
    @RequirePermission("broadcast.admin:update")
    public ApiResponse<BroadcastConfigResponse> toggle(@PathVariable String eventType) {
        return ApiResponse.ok(configService.toggle(eventType));
    }
}
```

- [ ] **Step 5: Compile**

Run: `cd backend && mvn -q -pl broadcast -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/ \
       backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/BroadcastConfigService.java
git commit -m "feat(broadcast): admin API for broadcast config management"
```

---

### Task 7: Payment Module Integration — Publish Deposit Broadcast

**Files:**
- Modify: `backend/payment/pom.xml` — add `fuoverflow-common` dependency (if not already present, for `BroadcastPublisher`)
- Modify: `backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java` — call `BroadcastPublisher.publish()` after crediting points

**Interfaces:**
- Consumes: `BroadcastPublisher` (Task 2), `BroadcastService.evaluateAndBroadcast()` (Task 5)
- Produces: Payment module fires `deposit.completed` broadcast after successful deposit

**Note:** The payment module should call `BroadcastService.evaluateAndBroadcast()` rather than publishing directly to Redis — the service handles config lookup, evaluation, message formatting, and audit logging. Since the broadcast module depends on common, and payment also depends on common, we use Spring's `ApplicationEventPublisher` as the bridge to avoid a circular dependency.

- [ ] **Step 1: Create `DepositCompletedEvent` in common module**

```java
package com.fuoverflow.common.broadcast;

import java.util.Map;
import java.util.UUID;

public record DepositCompletedEvent(
        UUID userId,
        String displayName,
        long amountVnd,
        long pointsEarned
) {
    public Map<String, Object> toEventData() {
        return Map.of(
                "userId", userId.toString(),
                "displayName", displayName,
                "amountVnd", amountVnd,
                "pointsEarned", pointsEarned
        );
    }
}
```

- [ ] **Step 2: Add event publishing in `PaymentService.creditPointsForPaidOrder`**

In `PaymentService.java`, inject `ApplicationEventPublisher` and a user-name-lookup interface. After `pointsWalletService.credit(...)`, publish the event:

Add to constructor parameters:
```java
private final ApplicationEventPublisher eventPublisher;
private final UserDisplayNameLookup userDisplayNameLookup;
```

Add at the end of `creditPointsForPaidOrder` method, after the `pointsWalletService.credit(...)` call:

```java
try {
    String displayName = userDisplayNameLookup.getDisplayName(order.getUserId());
    long amountVnd = order.getTotalCents() / 100;
    DepositCompletedEvent event = new DepositCompletedEvent(
            order.getUserId(), displayName, amountVnd, points);
    eventPublisher.publishEvent(event);
} catch (Exception e) {
    log.warn("Failed to publish deposit broadcast event", e);
}
```

- [ ] **Step 3: Create `UserDisplayNameLookup` interface in common module**

```java
package com.fuoverflow.common.broadcast;

import java.util.UUID;

public interface UserDisplayNameLookup {
    String getDisplayName(UUID userId);
}
```

- [ ] **Step 4: Implement `UserDisplayNameLookup` in user module**

Check where user entities live. Create an implementation that queries the user table:

```java
package com.fuoverflow.user.support;

import com.fuoverflow.common.broadcast.UserDisplayNameLookup;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JpaUserDisplayNameLookup implements UserDisplayNameLookup {

    private final UserRepository userRepository;

    public JpaUserDisplayNameLookup(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public String getDisplayName(UUID userId) {
        return userRepository.findById(userId)
                .map(UserEntity::getDisplayName)
                .orElse("Một thành viên");
    }
}
```

**Important:** Verify the actual method name for display name on `UserEntity` — it might be `getDisplayName()`, `getName()`, or `getHandle()`. Check the entity before implementing.

- [ ] **Step 5: Create event listener in broadcast module**

```java
package com.fuoverflow.broadcast.application;

import com.fuoverflow.common.broadcast.DepositCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class BroadcastEventHandler {

    private final BroadcastService broadcastService;

    public BroadcastEventHandler(BroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @Async
    @EventListener
    public void onDepositCompleted(DepositCompletedEvent event) {
        broadcastService.evaluateAndBroadcast(
                DepositBroadcastEvaluator.EVENT_TYPE,
                event.toEventData());
    }
}
```

- [ ] **Step 6: Compile the full project**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/common/src/main/java/com/fuoverflow/common/broadcast/DepositCompletedEvent.java \
       backend/common/src/main/java/com/fuoverflow/common/broadcast/UserDisplayNameLookup.java \
       backend/payment/src/main/java/com/fuoverflow/payment/application/PaymentService.java \
       backend/user/src/main/java/com/fuoverflow/user/support/JpaUserDisplayNameLookup.java \
       backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/BroadcastEventHandler.java
git commit -m "feat(payment): publish deposit.completed broadcast on successful deposit"
```

---

### Task 8: Tests

**Files:**
- Create: `backend/broadcast/src/test/java/com/fuoverflow/broadcast/application/DepositBroadcastEvaluatorTest.java`
- Create: `backend/broadcast/src/test/java/com/fuoverflow/broadcast/application/BroadcastServiceTest.java`
- Create: `backend/broadcast/src/test/java/com/fuoverflow/broadcast/infra/BroadcastEmitterPoolTest.java`

**Interfaces:**
- Consumes: All components from Tasks 2–7
- Produces: Unit tests for evaluator logic, service orchestration, and emitter pool lifecycle

- [ ] **Step 1: Write `DepositBroadcastEvaluatorTest`**

```java
package com.fuoverflow.broadcast.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DepositBroadcastEvaluatorTest {

    private DepositBroadcastEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new DepositBroadcastEvaluator();
    }

    @Test
    void returnsMessage_whenAmountExceedsThreshold() {
        Map<String, Object> config = Map.of("thresholdVnd", 500_000);
        Map<String, Object> eventData = Map.of(
                "amountVnd", 1_000_000,
                "displayName", "Nguyen Van A");

        String result = evaluator.evaluate(config, eventData);

        assertNotNull(result);
        assertTrue(result.contains("Nguyen Van A"));
        assertTrue(result.contains("1,000,000"));
    }

    @Test
    void returnsNull_whenAmountBelowThreshold() {
        Map<String, Object> config = Map.of("thresholdVnd", 500_000);
        Map<String, Object> eventData = Map.of("amountVnd", 100_000);

        assertNull(evaluator.evaluate(config, eventData));
    }

    @Test
    void usesCustomTemplate_whenProvided() {
        Map<String, Object> config = Map.of(
                "thresholdVnd", 0,
                "messageTemplate", "Chúc mừng %s đã nạp %,d VND!");
        Map<String, Object> eventData = Map.of(
                "amountVnd", 200_000,
                "displayName", "Test User");

        String result = evaluator.evaluate(config, eventData);

        assertTrue(result.startsWith("Chúc mừng Test User"));
    }

    @Test
    void usesDefaultDisplayName_whenMissing() {
        Map<String, Object> config = Map.of("thresholdVnd", 0);
        Map<String, Object> eventData = Map.of("amountVnd", 100_000);

        String result = evaluator.evaluate(config, eventData);

        assertTrue(result.contains("Một thành viên"));
    }

    @Test
    void returnsNull_whenThresholdExactlyEqualsAmount() {
        Map<String, Object> config = Map.of("thresholdVnd", 500_000);
        Map<String, Object> eventData = Map.of("amountVnd", 500_000);

        assertNull(evaluator.evaluate(config, eventData));
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd backend && mvn -q -pl broadcast test`
Expected: Tests pass (5 tests)

- [ ] **Step 3: Write `BroadcastEmitterPoolTest`**

```java
package com.fuoverflow.broadcast.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BroadcastEmitterPoolTest {

    private BroadcastEmitterPool pool;

    @BeforeEach
    void setUp() {
        pool = new BroadcastEmitterPool(new ObjectMapper());
    }

    @Test
    void register_increasesActiveCount() {
        assertEquals(0, pool.activeCount());
        pool.register(new SseEmitter());
        assertEquals(1, pool.activeCount());
    }

    @Test
    void completedEmitter_isRemovedFromPool() {
        SseEmitter emitter = new SseEmitter();
        pool.register(emitter);
        emitter.complete();
        assertEquals(0, pool.activeCount());
    }

    @Test
    void broadcast_doesNotThrow_whenPoolEmpty() {
        BroadcastMessage msg = new BroadcastMessage("test", "hello", Map.of());
        assertDoesNotThrow(() -> pool.broadcast(msg));
    }
}
```

- [ ] **Step 4: Run all broadcast tests**

Run: `cd backend && mvn -q -pl broadcast test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add backend/broadcast/src/test/
git commit -m "test(broadcast): unit tests for evaluator and emitter pool"
```

---

### Task 9: Full Integration Compile + Smoke Test

**Files:**
- No new files

**Interfaces:**
- Consumes: Everything from Tasks 1–8
- Produces: Verified compilation of entire project

- [ ] **Step 1: Full project compile**

Run: `cd backend && mvn -q -DskipTests package`
Expected: BUILD SUCCESS for all modules

- [ ] **Step 2: Run all tests**

Run: `cd backend && mvn -q test`
Expected: All tests pass

- [ ] **Step 3: Document the API for frontend**

Admin configures deposit broadcast threshold:

```http
PUT /api/v1/admin/broadcasts/configs/deposit.completed
Content-Type: application/json
Cookie: fuoverflow_at=<admin-jwt>

{
  "config": {
    "thresholdVnd": 500000,
    "messageTemplate": "%s vừa nạp %,d VND vào tài khoản!"
  },
  "enabled": true
}
```

Frontend subscribes to SSE stream:

```javascript
const evtSource = new EventSource('/api/v1/broadcasts/stream');
evtSource.addEventListener('deposit.completed', (e) => {
  const data = JSON.parse(e.data);
  showTicker(data.message); // marquee / ticker animation
});
```

- [ ] **Step 4: Final commit with any fixes**

```bash
git add -A
git commit -m "feat(broadcast): server-wide broadcast notification system with Redis Pub/Sub + SSE"
```

---

## Summary of Files

| Action | Path | Purpose |
|--------|------|---------|
| Create | `backend/app/src/main/resources/db/migration/V10__broadcast_system.sql` | Schema for configs + events |
| Create | `backend/common/.../broadcast/BroadcastMessage.java` | Shared message record |
| Create | `backend/common/.../broadcast/BroadcastPublisher.java` | Shared publisher interface |
| Create | `backend/common/.../broadcast/DepositCompletedEvent.java` | Domain event for deposit |
| Create | `backend/common/.../broadcast/UserDisplayNameLookup.java` | User name lookup interface |
| Create | `backend/broadcast/pom.xml` | Module POM |
| Create | `backend/broadcast/.../BroadcastModule.java` | Module marker |
| Create | `backend/broadcast/.../persistence/*Entity.java` | JPA entities |
| Create | `backend/broadcast/.../persistence/*Repository.java` | JPA repositories |
| Create | `backend/broadcast/.../infra/RedisBroadcastPublisher.java` | Redis publisher |
| Create | `backend/broadcast/.../infra/RedisBroadcastListener.java` | Redis subscriber |
| Create | `backend/broadcast/.../infra/BroadcastEmitterPool.java` | SSE emitter pool |
| Create | `backend/broadcast/.../config/BroadcastRedisConfig.java` | Redis listener config |
| Create | `backend/broadcast/.../application/BroadcastService.java` | Core service |
| Create | `backend/broadcast/.../application/BroadcastEvaluator.java` | Evaluator interface |
| Create | `backend/broadcast/.../application/BroadcastEvaluatorFactory.java` | Evaluator registry |
| Create | `backend/broadcast/.../application/DepositBroadcastEvaluator.java` | Deposit threshold logic |
| Create | `backend/broadcast/.../application/BroadcastEventHandler.java` | Spring event listener |
| Create | `backend/broadcast/.../application/BroadcastConfigService.java` | Admin config CRUD |
| Create | `backend/broadcast/.../api/BroadcastController.java` | SSE stream endpoint |
| Create | `backend/broadcast/.../api/BroadcastAdminController.java` | Admin config API |
| Create | `backend/broadcast/.../api/dto/BroadcastConfigRequest.java` | Request DTO |
| Create | `backend/broadcast/.../api/dto/BroadcastConfigResponse.java` | Response DTO |
| Create | `backend/user/.../support/JpaUserDisplayNameLookup.java` | User name impl |
| Modify | `backend/pom.xml` | Add broadcast module |
| Modify | `backend/app/pom.xml` | Add broadcast dependency |
| Modify | `backend/auth/.../SecurityConfig.java` | Permit SSE endpoint |
| Modify | `backend/payment/.../PaymentService.java` | Publish deposit event |

## Extensibility

To add a new broadcast type (e.g. `award.earned`, `membership.purchased`):

1. Create a new `XxxBroadcastEvaluator implements BroadcastEvaluator` with `@PostConstruct` registration
2. Create a new event record in common (e.g. `AwardEarnedEvent`)
3. Publish the event from the owning module via `ApplicationEventPublisher`
4. Add an `@EventListener` method in `BroadcastEventHandler`
5. Admin configures via `PUT /api/v1/admin/broadcasts/configs/award.earned`

No code changes needed in the broadcast infrastructure itself.
