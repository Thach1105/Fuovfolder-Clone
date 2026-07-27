# Server Announcements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add admin-managed server-wide announcements with rich text, scheduling, lifecycle, and marquee display for all users.

**Architecture:** New `announcements` table in existing broadcast module. Admin CRUD API + cron-based lifecycle scheduler. Reuse Redis pub/sub + SSE infrastructure to push `announcement.sync` events. Admin frontend gets TipTap rich text editor page. User frontend gets `AnnouncementBar` marquee component fed by SSE stream.

**Tech Stack:** Java 21, Spring Boot 3.5.x, PostgreSQL/Flyway, Redis pub/sub, SSE, Next.js 15 (admin), Next.js 16 (user), TipTap editor, DOMPurify.

## Global Constraints

- Java 21, Spring Boot 3.5.x, Maven multi-module monolith
- PostgreSQL only, Flyway migrations only, `ddl-auto=validate`
- No foreign key constraints in DB
- No Lombok — manual constructors, getters, factory methods
- Constructor injection only
- Return `ApiResponse<T>` from controllers via `ApiResponse.ok(...)`
- Permission checks via `@RequirePermission` annotation
- Admin frontend: Next.js 15, React 19, Tailwind v3, shadcn/ui components
- User frontend: Next.js 16, React 19, Tailwind v4

---

### Task 1: Database Migrations

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V47__announcement_system.sql`
- Create: `backend/app/src/main/resources/db/migration/V48__add_announcement_permissions.sql`

**Interfaces:**
- Consumes: nothing
- Produces: `announcements` table, permissions `announcement.admin:read` and `announcement.admin:write` seeded and granted to roles

- [ ] **Step 1: Create announcements table migration**

Create `backend/app/src/main/resources/db/migration/V47__announcement_system.sql`:

```sql
CREATE TABLE announcements (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title           varchar(200)  NOT NULL,
    content_html    text          NOT NULL,
    background_color varchar(9)   NOT NULL DEFAULT '#1e40af',
    link_url        varchar(2048),
    link_label      varchar(100),
    priority        int           NOT NULL DEFAULT 0,
    scroll_speed    int           NOT NULL DEFAULT 50,
    step_seconds    int           NOT NULL DEFAULT 300,
    status          varchar(16)   NOT NULL DEFAULT 'DRAFT',
    start_at        timestamptz   NOT NULL,
    end_at          timestamptz   NOT NULL,
    created_by      uuid          NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX ix_announcements_status_priority
    ON announcements(status, priority DESC);

CREATE INDEX ix_announcements_start_end
    ON announcements(start_at, end_at);

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_status
    CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'EXPIRED'));

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_end_after_start
    CHECK (end_at > start_at);

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_step_positive
    CHECK (step_seconds > 0);

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_scroll_speed_positive
    CHECK (scroll_speed > 0);
```

- [ ] **Step 2: Create permissions migration**

Create `backend/app/src/main/resources/db/migration/V48__add_announcement_permissions.sql`:

```sql
INSERT INTO permissions (slug, module, resource, action, description) VALUES
  ('announcement.admin:read',  'broadcast', 'announcement.admin', 'read',  'Admin: xem danh sach thong bao toan server'),
  ('announcement.admin:write', 'broadcast', 'announcement.admin', 'write', 'Admin: tao/sua/xoa thong bao toan server')
ON CONFLICT (slug) DO NOTHING;

UPDATE roles
SET permissions_json = permissions_json || '["announcement.admin:read","announcement.admin:write"]'::jsonb
WHERE slug = 'ADMIN'
  AND NOT (permissions_json @> '["announcement.admin:read"]'::jsonb);

UPDATE roles
SET permissions_json = permissions_json || '["announcement.admin:read"]'::jsonb
WHERE slug = 'SUB_ADMIN'
  AND NOT (permissions_json @> '["announcement.admin:read"]'::jsonb);
```

- [ ] **Step 3: Verify migrations run**

Run:
```bash
cd backend && mvn -q -DskipTests package
```
Expected: BUILD SUCCESS (Flyway runs migrations, Hibernate validates schema)

- [ ] **Step 4: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V47__announcement_system.sql backend/app/src/main/resources/db/migration/V48__add_announcement_permissions.sql
git commit -m "feat(broadcast): add announcements table and permissions migrations"
```

---

### Task 2: JPA Entity, Repository, and DTOs

**Files:**
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/persistence/AnnouncementEntity.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/persistence/AnnouncementRepository.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/AnnouncementRequest.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/AnnouncementResponse.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/AnnouncementActiveResponse.java`

**Interfaces:**
- Consumes: `announcements` table from Task 1
- Produces:
  - `AnnouncementEntity` — JPA entity with `create(...)`, `updateContent(...)`, `updateStatus(String)`
  - `AnnouncementRepository` — `findByStatus(String)`, `findByStatusAndStartAtLessThanEqual(String, Instant)`, `findByStatusAndEndAtLessThanEqual(String, Instant)`, `findByStatusOrderByPriorityDesc(String)`
  - `AnnouncementRequest` — validated request record
  - `AnnouncementResponse` — full admin response record
  - `AnnouncementActiveResponse` — user-facing response record (no title/status/createdBy/timestamps)

- [ ] **Step 1: Write entity test**

Create `backend/broadcast/src/test/java/com/fuoverflow/broadcast/persistence/AnnouncementEntityTest.java`:

```java
package com.fuoverflow.broadcast.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementEntityTest {

    @Test
    void create_setsAllFields() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.DAYS);
        UUID adminId = UUID.randomUUID();

        AnnouncementEntity entity = AnnouncementEntity.create(
                "Test Title", "<b>Hello</b>", "#ff0000",
                "https://example.com", "Click", 10, 60, 300,
                "SCHEDULED", start, end, adminId);

        assertEquals("Test Title", entity.getTitle());
        assertEquals("<b>Hello</b>", entity.getContentHtml());
        assertEquals("#ff0000", entity.getBackgroundColor());
        assertEquals("https://example.com", entity.getLinkUrl());
        assertEquals("Click", entity.getLinkLabel());
        assertEquals(10, entity.getPriority());
        assertEquals(60, entity.getScrollSpeed());
        assertEquals(300, entity.getStepSeconds());
        assertEquals("SCHEDULED", entity.getStatus());
        assertEquals(start, entity.getStartAt());
        assertEquals(end, entity.getEndAt());
        assertEquals(adminId, entity.getCreatedBy());
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    void updateContent_changesFieldsAndTimestamp() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.DAYS);
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Old</b>", "#000000",
                null, null, 0, 50, 300,
                "ACTIVE", start, end, UUID.randomUUID());

        Instant beforeUpdate = entity.getUpdatedAt();
        entity.updateContent("<b>New</b>", "#ffffff", "https://new.com", "New Link", 5, 80, 600);

        assertEquals("<b>New</b>", entity.getContentHtml());
        assertEquals("#ffffff", entity.getBackgroundColor());
        assertEquals("https://new.com", entity.getLinkUrl());
        assertEquals("New Link", entity.getLinkLabel());
        assertEquals(5, entity.getPriority());
        assertEquals(80, entity.getScrollSpeed());
        assertEquals(600, entity.getStepSeconds());
        assertTrue(entity.getUpdatedAt().compareTo(beforeUpdate) >= 0);
    }

    @Test
    void updateStatus_changesStatusAndTimestamp() {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.DAYS);
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#000000",
                null, null, 0, 50, 300,
                "SCHEDULED", start, end, UUID.randomUUID());

        entity.updateStatus("ACTIVE");
        assertEquals("ACTIVE", entity.getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl broadcast test -Dtest=AnnouncementEntityTest`
Expected: FAIL — `AnnouncementEntity` class not found

- [ ] **Step 3: Create AnnouncementEntity**

Create `backend/broadcast/src/main/java/com/fuoverflow/broadcast/persistence/AnnouncementEntity.java`:

```java
package com.fuoverflow.broadcast.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "announcements")
public class AnnouncementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content_html", nullable = false, columnDefinition = "text")
    private String contentHtml;

    @Column(name = "background_color", nullable = false, length = 9)
    private String backgroundColor;

    @Column(name = "link_url", length = 2048)
    private String linkUrl;

    @Column(name = "link_label", length = 100)
    private String linkLabel;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "scroll_speed", nullable = false)
    private int scrollSpeed;

    @Column(name = "step_seconds", nullable = false)
    private int stepSeconds;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AnnouncementEntity() {}

    public static AnnouncementEntity create(
            String title, String contentHtml, String backgroundColor,
            String linkUrl, String linkLabel, int priority, int scrollSpeed, int stepSeconds,
            String status, Instant startAt, Instant endAt, UUID createdBy) {
        AnnouncementEntity e = new AnnouncementEntity();
        e.title = title;
        e.contentHtml = contentHtml;
        e.backgroundColor = backgroundColor;
        e.linkUrl = linkUrl;
        e.linkLabel = linkLabel;
        e.priority = priority;
        e.scrollSpeed = scrollSpeed;
        e.stepSeconds = stepSeconds;
        e.status = status;
        e.startAt = startAt;
        e.endAt = endAt;
        e.createdBy = createdBy;
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public void updateContent(String contentHtml, String backgroundColor,
                              String linkUrl, String linkLabel,
                              int priority, int scrollSpeed, int stepSeconds) {
        this.contentHtml = contentHtml;
        this.backgroundColor = backgroundColor;
        this.linkUrl = linkUrl;
        this.linkLabel = linkLabel;
        this.priority = priority;
        this.scrollSpeed = scrollSpeed;
        this.stepSeconds = stepSeconds;
        this.updatedAt = Instant.now();
    }

    public void updateFull(String title, String contentHtml, String backgroundColor,
                           String linkUrl, String linkLabel,
                           int priority, int scrollSpeed, int stepSeconds,
                           Instant startAt, Instant endAt) {
        this.title = title;
        this.contentHtml = contentHtml;
        this.backgroundColor = backgroundColor;
        this.linkUrl = linkUrl;
        this.linkLabel = linkLabel;
        this.priority = priority;
        this.scrollSpeed = scrollSpeed;
        this.stepSeconds = stepSeconds;
        this.startAt = startAt;
        this.endAt = endAt;
        this.updatedAt = Instant.now();
    }

    public void updateStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getContentHtml() { return contentHtml; }
    public String getBackgroundColor() { return backgroundColor; }
    public String getLinkUrl() { return linkUrl; }
    public String getLinkLabel() { return linkLabel; }
    public int getPriority() { return priority; }
    public int getScrollSpeed() { return scrollSpeed; }
    public int getStepSeconds() { return stepSeconds; }
    public String getStatus() { return status; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q -pl broadcast test -Dtest=AnnouncementEntityTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Create AnnouncementRepository**

Create `backend/broadcast/src/main/java/com/fuoverflow/broadcast/persistence/AnnouncementRepository.java`:

```java
package com.fuoverflow.broadcast.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, UUID> {

    List<AnnouncementEntity> findByStatusOrderByPriorityDesc(String status);

    List<AnnouncementEntity> findByStatus(String status);

    List<AnnouncementEntity> findByStatusAndStartAtLessThanEqual(String status, Instant now);

    List<AnnouncementEntity> findByStatusAndEndAtLessThanEqual(String status, Instant now);
}
```

- [ ] **Step 6: Create DTOs**

Create `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/AnnouncementRequest.java`:

```java
package com.fuoverflow.broadcast.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AnnouncementRequest(
        @NotBlank String title,
        @NotBlank String contentHtml,
        @NotBlank String backgroundColor,
        String linkUrl,
        String linkLabel,
        int priority,
        @Min(10) int scrollSpeed,
        @Min(60) int stepSeconds,
        @NotNull Instant startAt,
        @NotNull Instant endAt
) {}
```

Create `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/AnnouncementResponse.java`:

```java
package com.fuoverflow.broadcast.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementResponse(
        UUID id,
        String title,
        String contentHtml,
        String backgroundColor,
        String linkUrl,
        String linkLabel,
        int priority,
        int scrollSpeed,
        int stepSeconds,
        String status,
        Instant startAt,
        Instant endAt,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
```

Create `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/AnnouncementActiveResponse.java`:

```java
package com.fuoverflow.broadcast.api.dto;

import java.util.UUID;

public record AnnouncementActiveResponse(
        UUID id,
        String contentHtml,
        String backgroundColor,
        String linkUrl,
        String linkLabel,
        int priority,
        int scrollSpeed,
        int stepSeconds
) {}
```

- [ ] **Step 7: Verify build**

Run: `cd backend && mvn -q -DskipTests package`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add backend/broadcast/src/main/java/com/fuoverflow/broadcast/persistence/AnnouncementEntity.java \
       backend/broadcast/src/main/java/com/fuoverflow/broadcast/persistence/AnnouncementRepository.java \
       backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/AnnouncementRequest.java \
       backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/AnnouncementResponse.java \
       backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/dto/AnnouncementActiveResponse.java \
       backend/broadcast/src/test/java/com/fuoverflow/broadcast/persistence/AnnouncementEntityTest.java
git commit -m "feat(broadcast): add announcement entity, repository, and DTOs"
```

---

### Task 3: AnnouncementService + AnnouncementScheduler

**Files:**
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/AnnouncementService.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/AnnouncementScheduler.java`
- Create: `backend/broadcast/src/test/java/com/fuoverflow/broadcast/application/AnnouncementServiceTest.java`

**Interfaces:**
- Consumes:
  - `AnnouncementEntity` — `create(...)`, `updateContent(...)`, `updateFull(...)`, `updateStatus(String)`
  - `AnnouncementRepository` — all query methods from Task 2
  - `BroadcastPublisher.publish(BroadcastMessage)` from common module
  - `ObjectMapper` for JSON serialization
- Produces:
  - `AnnouncementService.create(AnnouncementRequest, String status, UUID adminUserId) -> AnnouncementResponse`
  - `AnnouncementService.update(UUID id, AnnouncementRequest) -> AnnouncementResponse`
  - `AnnouncementService.delete(UUID id) -> void`
  - `AnnouncementService.activate(UUID id) -> AnnouncementResponse`
  - `AnnouncementService.deactivate(UUID id) -> AnnouncementResponse`
  - `AnnouncementService.listByStatus(String status) -> List<AnnouncementResponse>` (null status = all)
  - `AnnouncementService.getById(UUID id) -> AnnouncementResponse`
  - `AnnouncementService.getActiveForUsers() -> List<AnnouncementActiveResponse>`
  - `AnnouncementService.publishSync() -> void`
  - `AnnouncementService.sendInitialState(SseEmitter emitter) -> void`
  - `AnnouncementScheduler` — `@Scheduled` cron job, transitions SCHEDULED→ACTIVE and ACTIVE→EXPIRED

- [ ] **Step 1: Write service tests**

Create `backend/broadcast/src/test/java/com/fuoverflow/broadcast/application/AnnouncementServiceTest.java`:

```java
package com.fuoverflow.broadcast.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.broadcast.api.dto.AnnouncementActiveResponse;
import com.fuoverflow.broadcast.api.dto.AnnouncementRequest;
import com.fuoverflow.broadcast.api.dto.AnnouncementResponse;
import com.fuoverflow.broadcast.persistence.AnnouncementEntity;
import com.fuoverflow.broadcast.persistence.AnnouncementRepository;
import com.fuoverflow.common.broadcast.BroadcastPublisher;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnouncementServiceTest {

    private AnnouncementRepository repo;
    private BroadcastPublisher publisher;
    private AnnouncementService service;
    private final UUID adminId = UUID.randomUUID();
    private final Instant futureStart = Instant.now().plus(1, ChronoUnit.HOURS);
    private final Instant futureEnd = Instant.now().plus(2, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        repo = mock(AnnouncementRepository.class);
        publisher = mock(BroadcastPublisher.class);
        service = new AnnouncementService(repo, publisher, new ObjectMapper());
    }

    private AnnouncementRequest validRequest() {
        return new AnnouncementRequest(
                "Test", "<b>Hello</b>", "#ff0000",
                "https://example.com", "Click", 10, 50, 300,
                futureStart, futureEnd);
    }

    @Test
    void create_scheduled_savesWithCorrectStatus() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByStatusOrderByPriorityDesc("ACTIVE")).thenReturn(List.of());

        AnnouncementResponse result = service.create(validRequest(), "SCHEDULED", adminId);

        assertEquals("SCHEDULED", result.status());
        assertEquals("Test", result.title());
        verify(publisher, never()).publish(any());
    }

    @Test
    void create_active_publishesSync() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByStatusOrderByPriorityDesc("ACTIVE")).thenReturn(List.of());

        service.create(validRequest(), "ACTIVE", adminId);

        verify(publisher).publish(any());
    }

    @Test
    void create_endBeforeStart_throws() {
        AnnouncementRequest bad = new AnnouncementRequest(
                "Test", "<b>Hello</b>", "#ff0000",
                null, null, 0, 50, 300,
                futureEnd, futureStart);

        assertThrows(BadRequestException.class, () -> service.create(bad, "DRAFT", adminId));
    }

    @Test
    void delete_activeStatus_throws() {
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#000", null, null, 0, 50, 300,
                "ACTIVE", futureStart, futureEnd, adminId);
        when(repo.findById(any())).thenReturn(Optional.of(entity));

        assertThrows(BadRequestException.class, () -> service.delete(UUID.randomUUID()));
    }

    @Test
    void delete_draftStatus_succeeds() {
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#000", null, null, 0, 50, 300,
                "DRAFT", futureStart, futureEnd, adminId);
        when(repo.findById(any())).thenReturn(Optional.of(entity));

        service.delete(UUID.randomUUID());

        verify(repo).delete(entity);
    }

    @Test
    void activate_changesStatusAndPublishes() {
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#000", null, null, 0, 50, 300,
                "SCHEDULED", futureStart, futureEnd, adminId);
        when(repo.findById(any())).thenReturn(Optional.of(entity));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByStatusOrderByPriorityDesc("ACTIVE")).thenReturn(List.of(entity));

        AnnouncementResponse result = service.activate(UUID.randomUUID());

        assertEquals("ACTIVE", result.status());
        verify(publisher).publish(any());
    }

    @Test
    void deactivate_changesStatusAndPublishes() {
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#000", null, null, 0, 50, 300,
                "ACTIVE", futureStart, futureEnd, adminId);
        when(repo.findById(any())).thenReturn(Optional.of(entity));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repo.findByStatusOrderByPriorityDesc("ACTIVE")).thenReturn(List.of());

        AnnouncementResponse result = service.deactivate(UUID.randomUUID());

        assertEquals("EXPIRED", result.status());
        verify(publisher).publish(any());
    }

    @Test
    void getById_notFound_throws() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getById(UUID.randomUUID()));
    }

    @Test
    void getActiveForUsers_mapsToActiveResponse() {
        AnnouncementEntity entity = AnnouncementEntity.create(
                "Title", "<b>Test</b>", "#123456",
                "https://link.com", "Go", 5, 60, 120,
                "ACTIVE", futureStart, futureEnd, adminId);
        when(repo.findByStatusOrderByPriorityDesc("ACTIVE")).thenReturn(List.of(entity));

        List<AnnouncementActiveResponse> result = service.getActiveForUsers();

        assertEquals(1, result.size());
        AnnouncementActiveResponse r = result.getFirst();
        assertEquals("<b>Test</b>", r.contentHtml());
        assertEquals("#123456", r.backgroundColor());
        assertEquals("https://link.com", r.linkUrl());
        assertEquals("Go", r.linkLabel());
        assertEquals(5, r.priority());
        assertEquals(60, r.scrollSpeed());
        assertEquals(120, r.stepSeconds());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl broadcast test -Dtest=AnnouncementServiceTest`
Expected: FAIL — `AnnouncementService` not found

- [ ] **Step 3: Implement AnnouncementService**

Create `backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/AnnouncementService.java`:

```java
package com.fuoverflow.broadcast.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.broadcast.api.dto.AnnouncementActiveResponse;
import com.fuoverflow.broadcast.api.dto.AnnouncementRequest;
import com.fuoverflow.broadcast.api.dto.AnnouncementResponse;
import com.fuoverflow.broadcast.persistence.AnnouncementEntity;
import com.fuoverflow.broadcast.persistence.AnnouncementRepository;
import com.fuoverflow.common.broadcast.BroadcastMessage;
import com.fuoverflow.common.broadcast.BroadcastPublisher;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnnouncementService {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);
    private static final String EVENT_TYPE = "announcement.sync";

    private final AnnouncementRepository repo;
    private final BroadcastPublisher publisher;
    private final ObjectMapper objectMapper;

    public AnnouncementService(AnnouncementRepository repo,
                               BroadcastPublisher publisher,
                               ObjectMapper objectMapper) {
        this.repo = repo;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request, String status, UUID adminUserId) {
        validateTimeRange(request);
        AnnouncementEntity entity = AnnouncementEntity.create(
                request.title(), request.contentHtml(), request.backgroundColor(),
                request.linkUrl(), request.linkLabel(),
                request.priority(), request.scrollSpeed(), request.stepSeconds(),
                status, request.startAt(), request.endAt(), adminUserId);
        AnnouncementResponse response = toResponse(repo.save(entity));
        if ("ACTIVE".equals(status)) {
            publishSync();
        }
        return response;
    }

    @Transactional
    public AnnouncementResponse update(UUID id, AnnouncementRequest request) {
        validateTimeRange(request);
        AnnouncementEntity entity = findById(id);
        String currentStatus = entity.getStatus();

        if ("DRAFT".equals(currentStatus) || "SCHEDULED".equals(currentStatus)) {
            entity.updateFull(
                    request.title(), request.contentHtml(), request.backgroundColor(),
                    request.linkUrl(), request.linkLabel(),
                    request.priority(), request.scrollSpeed(), request.stepSeconds(),
                    request.startAt(), request.endAt());
        } else if ("ACTIVE".equals(currentStatus)) {
            entity.updateContent(
                    request.contentHtml(), request.backgroundColor(),
                    request.linkUrl(), request.linkLabel(),
                    request.priority(), request.scrollSpeed(), request.stepSeconds());
        } else {
            throw new BadRequestException("ANNOUNCEMENT_NOT_EDITABLE",
                    "Cannot edit announcement with status: " + currentStatus);
        }

        AnnouncementResponse response = toResponse(repo.save(entity));
        if ("ACTIVE".equals(currentStatus)) {
            publishSync();
        }
        return response;
    }

    @Transactional
    public void delete(UUID id) {
        AnnouncementEntity entity = findById(id);
        if ("ACTIVE".equals(entity.getStatus()) || "EXPIRED".equals(entity.getStatus())) {
            throw new BadRequestException("ANNOUNCEMENT_CANNOT_DELETE",
                    "Cannot delete announcement with status: " + entity.getStatus());
        }
        repo.delete(entity);
    }

    @Transactional
    public AnnouncementResponse activate(UUID id) {
        AnnouncementEntity entity = findById(id);
        entity.updateStatus("ACTIVE");
        AnnouncementResponse response = toResponse(repo.save(entity));
        publishSync();
        return response;
    }

    @Transactional
    public AnnouncementResponse deactivate(UUID id) {
        AnnouncementEntity entity = findById(id);
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new BadRequestException("ANNOUNCEMENT_NOT_ACTIVE",
                    "Can only deactivate an active announcement");
        }
        entity.updateStatus("EXPIRED");
        AnnouncementResponse response = toResponse(repo.save(entity));
        publishSync();
        return response;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listByStatus(String status) {
        List<AnnouncementEntity> entities = (status == null || status.isBlank())
                ? repo.findAll()
                : repo.findByStatus(status);
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse getById(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public List<AnnouncementActiveResponse> getActiveForUsers() {
        return repo.findByStatusOrderByPriorityDesc("ACTIVE").stream()
                .map(this::toActiveResponse)
                .toList();
    }

    public void publishSync() {
        List<AnnouncementActiveResponse> active = getActiveForUsers();
        Map<String, Object> data = Map.of("announcements", active);
        String message;
        try {
            message = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize announcement sync payload", e);
            return;
        }
        publisher.publish(new BroadcastMessage(EVENT_TYPE, message, data));
    }

    public void sendInitialState(SseEmitter emitter) {
        List<AnnouncementActiveResponse> active = getActiveForUsers();
        if (active.isEmpty()) return;
        Map<String, Object> data = Map.of("announcements", active);
        try {
            String json = objectMapper.writeValueAsString(
                    new BroadcastMessage(EVENT_TYPE, objectMapper.writeValueAsString(data), data));
            emitter.send(SseEmitter.event().name(EVENT_TYPE).data(json));
        } catch (IOException e) {
            log.debug("Failed to send initial announcement state", e);
        }
    }

    private void validateTimeRange(AnnouncementRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BadRequestException("INVALID_TIME_RANGE",
                    "End time must be after start time");
        }
    }

    private AnnouncementEntity findById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "ANNOUNCEMENT_NOT_FOUND", "Announcement not found: " + id));
    }

    private AnnouncementResponse toResponse(AnnouncementEntity e) {
        return new AnnouncementResponse(
                e.getId(), e.getTitle(), e.getContentHtml(), e.getBackgroundColor(),
                e.getLinkUrl(), e.getLinkLabel(), e.getPriority(), e.getScrollSpeed(),
                e.getStepSeconds(), e.getStatus(), e.getStartAt(), e.getEndAt(),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private AnnouncementActiveResponse toActiveResponse(AnnouncementEntity e) {
        return new AnnouncementActiveResponse(
                e.getId(), e.getContentHtml(), e.getBackgroundColor(),
                e.getLinkUrl(), e.getLinkLabel(), e.getPriority(),
                e.getScrollSpeed(), e.getStepSeconds());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl broadcast test -Dtest=AnnouncementServiceTest`
Expected: PASS (8 tests)

- [ ] **Step 5: Create AnnouncementScheduler**

Create `backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/AnnouncementScheduler.java`:

```java
package com.fuoverflow.broadcast.application;

import com.fuoverflow.broadcast.persistence.AnnouncementEntity;
import com.fuoverflow.broadcast.persistence.AnnouncementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class AnnouncementScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementScheduler.class);

    private final AnnouncementRepository repo;
    private final AnnouncementService announcementService;

    public AnnouncementScheduler(AnnouncementRepository repo,
                                 AnnouncementService announcementService) {
        this.repo = repo;
        this.announcementService = announcementService;
    }

    @Scheduled(fixedDelayString = "${fuexam.announcement.scan-interval-ms:60000}")
    @Transactional
    public void processLifecycleTransitions() {
        Instant now = Instant.now();
        boolean changed = false;

        List<AnnouncementEntity> toActivate =
                repo.findByStatusAndStartAtLessThanEqual("SCHEDULED", now);
        for (AnnouncementEntity entity : toActivate) {
            entity.updateStatus("ACTIVE");
            repo.save(entity);
            log.info("Announcement activated: id={}, title={}", entity.getId(), entity.getTitle());
            changed = true;
        }

        List<AnnouncementEntity> toExpire =
                repo.findByStatusAndEndAtLessThanEqual("ACTIVE", now);
        for (AnnouncementEntity entity : toExpire) {
            entity.updateStatus("EXPIRED");
            repo.save(entity);
            log.info("Announcement expired: id={}, title={}", entity.getId(), entity.getTitle());
            changed = true;
        }

        if (changed) {
            announcementService.publishSync();
        }
    }
}
```

- [ ] **Step 6: Verify build**

Run: `cd backend && mvn -q -DskipTests package`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/AnnouncementService.java \
       backend/broadcast/src/main/java/com/fuoverflow/broadcast/application/AnnouncementScheduler.java \
       backend/broadcast/src/test/java/com/fuoverflow/broadcast/application/AnnouncementServiceTest.java
git commit -m "feat(broadcast): add AnnouncementService with lifecycle management and scheduler"
```

---

### Task 4: Admin Controller + SSE Initial State + Security Config

**Files:**
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/AnnouncementAdminController.java`
- Create: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/AnnouncementPublicController.java`
- Modify: `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/BroadcastController.java:22-28`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java:120-121`

**Interfaces:**
- Consumes:
  - `AnnouncementService` — all methods from Task 3
  - `SecurityContextHolder` / `AuthContext` pattern for getting admin user ID
  - `ApiResponse.ok(...)` for response wrapping
  - `@RequirePermission` for RBAC
- Produces:
  - Admin REST API at `/api/v1/admin/announcements`
  - Public REST API at `GET /api/v1/announcements/active`
  - SSE initial state sent on new connection

- [ ] **Step 1: Create AnnouncementAdminController**

Create `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/AnnouncementAdminController.java`:

```java
package com.fuoverflow.broadcast.api;

import com.fuoverflow.broadcast.api.dto.AnnouncementActiveResponse;
import com.fuoverflow.broadcast.api.dto.AnnouncementRequest;
import com.fuoverflow.broadcast.api.dto.AnnouncementResponse;
import com.fuoverflow.broadcast.application.AnnouncementService;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/announcements")
@RequirePermission("admin.panel:access")
public class AnnouncementAdminController {

    private final AnnouncementService announcementService;

    public AnnouncementAdminController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    @RequirePermission("announcement.admin:read")
    public ApiResponse<List<AnnouncementResponse>> list(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(announcementService.listByStatus(status));
    }

    @GetMapping("/{id}")
    @RequirePermission("announcement.admin:read")
    public ApiResponse<AnnouncementResponse> getById(@PathVariable UUID id) {
        return ApiResponse.ok(announcementService.getById(id));
    }

    @PostMapping
    @RequirePermission("announcement.admin:write")
    public ApiResponse<AnnouncementResponse> create(
            @Valid @RequestBody AnnouncementRequest request,
            @RequestParam(defaultValue = "DRAFT") String status) {
        return ApiResponse.ok(announcementService.create(request, status, currentUserId()));
    }

    @PutMapping("/{id}")
    @RequirePermission("announcement.admin:write")
    public ApiResponse<AnnouncementResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AnnouncementRequest request) {
        return ApiResponse.ok(announcementService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("announcement.admin:write")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        announcementService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/activate")
    @RequirePermission("announcement.admin:write")
    public ApiResponse<AnnouncementResponse> activate(@PathVariable UUID id) {
        return ApiResponse.ok(announcementService.activate(id));
    }

    @PostMapping("/{id}/deactivate")
    @RequirePermission("announcement.admin:write")
    public ApiResponse<AnnouncementResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.ok(announcementService.deactivate(id));
    }

    @GetMapping("/{id}/preview")
    @RequirePermission("announcement.admin:read")
    public ApiResponse<AnnouncementActiveResponse> preview(@PathVariable UUID id) {
        AnnouncementResponse full = announcementService.getById(id);
        return ApiResponse.ok(new AnnouncementActiveResponse(
                full.id(), full.contentHtml(), full.backgroundColor(),
                full.linkUrl(), full.linkLabel(), full.priority(),
                full.scrollSpeed(), full.stepSeconds()));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new UnauthorizedException("AUTH_REQUIRED", "Authentication required");
        }
        return UUID.fromString(auth.getName());
    }
}
```

- [ ] **Step 2: Create AnnouncementPublicController**

Create `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/AnnouncementPublicController.java`:

```java
package com.fuoverflow.broadcast.api;

import com.fuoverflow.broadcast.api.dto.AnnouncementActiveResponse;
import com.fuoverflow.broadcast.application.AnnouncementService;
import com.fuoverflow.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementPublicController {

    private final AnnouncementService announcementService;

    public AnnouncementPublicController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/active")
    public ApiResponse<List<AnnouncementActiveResponse>> getActive() {
        return ApiResponse.ok(announcementService.getActiveForUsers());
    }
}
```

- [ ] **Step 3: Modify BroadcastController to send initial announcement state**

In `backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/BroadcastController.java`, add `AnnouncementService` injection and call `sendInitialState` after registering the emitter:

```java
package com.fuoverflow.broadcast.api;

import com.fuoverflow.broadcast.application.AnnouncementService;
import com.fuoverflow.broadcast.infra.BroadcastEmitterPool;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/broadcasts")
public class BroadcastController {

    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final BroadcastEmitterPool emitterPool;
    private final AnnouncementService announcementService;

    public BroadcastController(BroadcastEmitterPool emitterPool,
                               AnnouncementService announcementService) {
        this.emitterPool = emitterPool;
        this.announcementService = announcementService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitterPool.register(emitter);
        announcementService.sendInitialState(emitter);
        return emitter;
    }
}
```

- [ ] **Step 4: Add public announcement endpoint to SecurityConfig**

In `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java`, add the announcements active endpoint to the permitAll section. Find the line:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/broadcasts/stream")
.permitAll()
```

Change it to:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/broadcasts/stream",
        "/api/v1/announcements/active")
.permitAll()
```

- [ ] **Step 5: Verify build**

Run: `cd backend && mvn -q -DskipTests package`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/AnnouncementAdminController.java \
       backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/AnnouncementPublicController.java \
       backend/broadcast/src/main/java/com/fuoverflow/broadcast/api/BroadcastController.java \
       backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java
git commit -m "feat(broadcast): add announcement admin/public controllers and SSE initial state"
```

---

### Task 5: Admin Frontend — API Client, Types, Sidebar

**Files:**
- Create: `Fuexam-admin/lib/api/admin-announcements.ts`
- Modify: `Fuexam-admin/types/api.ts:269` — add `AnnouncementResponse` and `AnnouncementActiveResponse` types
- Modify: `Fuexam-admin/components/admin/AdminSidebar.tsx:125` — add sidebar link

**Interfaces:**
- Consumes: Admin API endpoints from Task 4, `apiFetch` from `@/lib/api/client`
- Produces:
  - `listAnnouncements(status?: string) -> AnnouncementResponse[]`
  - `getAnnouncement(id: string) -> AnnouncementResponse`
  - `createAnnouncement(request, status) -> AnnouncementResponse`
  - `updateAnnouncement(id, request) -> AnnouncementResponse`
  - `deleteAnnouncement(id) -> void`
  - `activateAnnouncement(id) -> AnnouncementResponse`
  - `deactivateAnnouncement(id) -> AnnouncementResponse`
  - `previewAnnouncement(id) -> AnnouncementActiveResponse`
  - TypeScript types for both response shapes
  - Sidebar nav item

- [ ] **Step 1: Add TypeScript types**

In `Fuexam-admin/types/api.ts`, after the `BroadcastConfigResponse` interface (~line 269), add:

```typescript
export interface AnnouncementResponse {
  id: string;
  title: string;
  contentHtml: string;
  backgroundColor: string;
  linkUrl: string | null;
  linkLabel: string | null;
  priority: number;
  scrollSpeed: number;
  stepSeconds: number;
  status: "DRAFT" | "SCHEDULED" | "ACTIVE" | "EXPIRED";
  startAt: string;
  endAt: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AnnouncementActiveResponse {
  id: string;
  contentHtml: string;
  backgroundColor: string;
  linkUrl: string | null;
  linkLabel: string | null;
  priority: number;
  scrollSpeed: number;
  stepSeconds: number;
}
```

- [ ] **Step 2: Create API client**

Create `Fuexam-admin/lib/api/admin-announcements.ts`:

```typescript
import { apiFetch } from "@/lib/api/client";
import type { AnnouncementActiveResponse, AnnouncementResponse } from "@/types/api";

export async function listAnnouncements(status?: string): Promise<AnnouncementResponse[]> {
  const params = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiFetch<AnnouncementResponse[]>(`/api/v1/admin/announcements${params}`);
}

export async function getAnnouncement(id: string): Promise<AnnouncementResponse> {
  return apiFetch<AnnouncementResponse>(`/api/v1/admin/announcements/${id}`);
}

export async function createAnnouncement(
  body: {
    title: string;
    contentHtml: string;
    backgroundColor: string;
    linkUrl?: string;
    linkLabel?: string;
    priority: number;
    scrollSpeed: number;
    stepSeconds: number;
    startAt: string;
    endAt: string;
  },
  status: string,
): Promise<AnnouncementResponse> {
  return apiFetch<AnnouncementResponse>(
    `/api/v1/admin/announcements?status=${encodeURIComponent(status)}`,
    { method: "POST", body: JSON.stringify(body) },
  );
}

export async function updateAnnouncement(
  id: string,
  body: {
    title: string;
    contentHtml: string;
    backgroundColor: string;
    linkUrl?: string;
    linkLabel?: string;
    priority: number;
    scrollSpeed: number;
    stepSeconds: number;
    startAt: string;
    endAt: string;
  },
): Promise<AnnouncementResponse> {
  return apiFetch<AnnouncementResponse>(`/api/v1/admin/announcements/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function deleteAnnouncement(id: string): Promise<void> {
  await apiFetch<void>(`/api/v1/admin/announcements/${id}`, { method: "DELETE" });
}

export async function activateAnnouncement(id: string): Promise<AnnouncementResponse> {
  return apiFetch<AnnouncementResponse>(`/api/v1/admin/announcements/${id}/activate`, {
    method: "POST",
  });
}

export async function deactivateAnnouncement(id: string): Promise<AnnouncementResponse> {
  return apiFetch<AnnouncementResponse>(`/api/v1/admin/announcements/${id}/deactivate`, {
    method: "POST",
  });
}

export async function previewAnnouncement(id: string): Promise<AnnouncementActiveResponse> {
  return apiFetch<AnnouncementActiveResponse>(`/api/v1/admin/announcements/${id}/preview`);
}
```

- [ ] **Step 3: Add sidebar link**

In `Fuexam-admin/components/admin/AdminSidebar.tsx`, find the broadcast entry (~line 121-125):

```typescript
{
  href: "/broadcasts/configs",
  label: "Thông báo server",
  icon: Radio,
  permission: "broadcast.admin:read",
},
```

Add a new entry right after it (before the voucher entry):

```typescript
{
  href: "/announcements",
  label: "Thông báo toàn server",
  icon: Megaphone,
  permission: "announcement.admin:read",
},
```

Also add `Megaphone` to the `lucide-react` imports at the top of the file.

- [ ] **Step 4: Verify admin frontend builds**

Run: `cd Fuexam-admin && npm run build`
Expected: Build succeeds (new page not yet created, but types/API/sidebar are valid)

- [ ] **Step 5: Commit**

```bash
git add Fuexam-admin/types/api.ts \
       Fuexam-admin/lib/api/admin-announcements.ts \
       Fuexam-admin/components/admin/AdminSidebar.tsx
git commit -m "feat(admin): add announcement API client, types, and sidebar link"
```

---

### Task 6: Admin Frontend — Announcements List Page

**Files:**
- Create: `Fuexam-admin/app/announcements/page.tsx`

**Interfaces:**
- Consumes:
  - `listAnnouncements(status?)` from `@/lib/api/admin-announcements`
  - `activateAnnouncement(id)`, `deactivateAnnouncement(id)`, `deleteAnnouncement(id)`
  - `AnnouncementResponse` type
  - `AdminShell` component, shadcn/ui `Card`, `Button`, `Tabs`
  - `can(user, permission)` from `@/lib/auth/permissions`
- Produces: `/announcements` page with tab filtering, status badges, action buttons

- [ ] **Step 1: Create the list page**

Create `Fuexam-admin/app/announcements/page.tsx`:

```tsx
"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ApiError } from "@/lib/api/client";
import * as announcementApi from "@/lib/api/admin-announcements";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type { AnnouncementResponse } from "@/types/api";

const STATUS_TABS = [
  { value: "", label: "Tất cả" },
  { value: "DRAFT", label: "Nháp" },
  { value: "SCHEDULED", label: "Đã lên lịch" },
  { value: "ACTIVE", label: "Đang chạy" },
  { value: "EXPIRED", label: "Đã hết hạn" },
];

const STATUS_BADGE: Record<string, string> = {
  DRAFT: "bg-muted text-muted-foreground",
  SCHEDULED: "bg-amber-500/15 text-amber-500",
  ACTIVE: "bg-emerald-500/15 text-emerald-500",
  EXPIRED: "bg-red-500/15 text-red-500",
};

const STATUS_LABEL: Record<string, string> = {
  DRAFT: "Nháp",
  SCHEDULED: "Đã lên lịch",
  ACTIVE: "Đang chạy",
  EXPIRED: "Đã hết hạn",
};

function formatDate(iso: string) {
  return new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

export default function AnnouncementsPage() {
  const { user } = useAuth();
  const router = useRouter();
  const canWrite = can(user, "announcement.admin:write");

  const [items, setItems] = useState<AnnouncementResponse[]>([]);
  const [tab, setTab] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await announcementApi.listAnnouncements(tab || undefined));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách.");
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => { load(); }, [load]);

  const handleActivate = async (id: string) => {
    try {
      await announcementApi.activateAnnouncement(id);
      toast.success("Đã kích hoạt thông báo.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Thao tác thất bại.");
    }
  };

  const handleDeactivate = async (id: string) => {
    try {
      await announcementApi.deactivateAnnouncement(id);
      toast.success("Đã dừng thông báo.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Thao tác thất bại.");
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Xóa thông báo này?")) return;
    try {
      await announcementApi.deleteAnnouncement(id);
      toast.success("Đã xóa.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    }
  };

  return (
    <AdminShell
      title="Thông báo toàn server"
      description="Quản lý thông báo marquee hiển thị cho tất cả người dùng"
    >
      <div className="mb-4 flex items-center justify-between">
        <div className="flex gap-1">
          {STATUS_TABS.map((t) => (
            <button
              key={t.value}
              onClick={() => setTab(t.value)}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                tab === t.value
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground hover:bg-muted/80"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        {canWrite && (
          <Button size="sm" onClick={() => router.push("/announcements/new")}>
            + Tạo thông báo
          </Button>
        )}
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {!loading && items.length === 0 && (
        <p className="text-sm text-muted-foreground">Chưa có thông báo nào.</p>
      )}

      {!loading && (
        <div className="space-y-3">
          {items.map((item) => (
            <Card key={item.id}>
              <CardContent className="p-4">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold text-foreground">{item.title}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {formatDate(item.startAt)} → {formatDate(item.endAt)} &middot; lặp mỗi{" "}
                      {Math.round(item.stepSeconds / 60)} phút &middot; ưu tiên: {item.priority}
                    </p>
                  </div>
                  <span
                    className={`shrink-0 rounded px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${STATUS_BADGE[item.status] ?? ""}`}
                  >
                    {STATUS_LABEL[item.status] ?? item.status}
                  </span>
                </div>

                <div
                  className="mt-3 overflow-hidden rounded px-3 py-1.5 text-sm text-white"
                  style={{ backgroundColor: item.backgroundColor }}
                  dangerouslySetInnerHTML={{ __html: item.contentHtml }}
                />

                {canWrite && (
                  <div className="mt-3 flex items-center gap-3 text-sm">
                    <button
                      className="text-primary hover:underline"
                      onClick={() => router.push(`/announcements/${item.id}/edit`)}
                    >
                      Sửa
                    </button>
                    {item.status === "SCHEDULED" && (
                      <button
                        className="text-emerald-500 hover:underline"
                        onClick={() => handleActivate(item.id)}
                      >
                        Kích hoạt ngay
                      </button>
                    )}
                    {item.status === "ACTIVE" && (
                      <button
                        className="text-amber-500 hover:underline"
                        onClick={() => handleDeactivate(item.id)}
                      >
                        Dừng sớm
                      </button>
                    )}
                    {(item.status === "DRAFT" || item.status === "SCHEDULED") && (
                      <button
                        className="text-destructive hover:underline"
                        onClick={() => handleDelete(item.id)}
                      >
                        Xóa
                      </button>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </AdminShell>
  );
}
```

- [ ] **Step 2: Verify build**

Run: `cd Fuexam-admin && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add Fuexam-admin/app/announcements/page.tsx
git commit -m "feat(admin): add announcements list page with status tabs and actions"
```

---

### Task 7: Admin Frontend — Create/Edit Page with TipTap Editor

**Files:**
- Create: `Fuexam-admin/app/announcements/new/page.tsx`
- Create: `Fuexam-admin/app/announcements/[id]/edit/page.tsx`
- Create: `Fuexam-admin/components/admin/AnnouncementForm.tsx`
- Create: `Fuexam-admin/components/admin/AnnouncementPreview.tsx`

**Interfaces:**
- Consumes:
  - `createAnnouncement(body, status)`, `updateAnnouncement(id, body)`, `getAnnouncement(id)` from API client
  - TipTap: `@tiptap/react`, `@tiptap/starter-kit`, `@tiptap/extension-color`, `@tiptap/extension-text-style`
  - shadcn/ui: `Input`, `Label`, `Button`, `Card`
- Produces:
  - `AnnouncementForm` — reusable form with TipTap editor, color picker, datetime inputs, live preview
  - `/announcements/new` page — creates new announcement
  - `/announcements/[id]/edit` page — edits existing announcement

- [ ] **Step 1: Install TipTap dependencies**

Run:
```bash
cd Fuexam-admin && npm install @tiptap/react @tiptap/starter-kit @tiptap/extension-color @tiptap/extension-text-style @tiptap/pm
```

- [ ] **Step 2: Create AnnouncementPreview component**

Create `Fuexam-admin/components/admin/AnnouncementPreview.tsx`:

```tsx
"use client";

import { useEffect, useRef, useState } from "react";

interface AnnouncementPreviewProps {
  contentHtml: string;
  backgroundColor: string;
  scrollSpeed: number;
}

export function AnnouncementPreview({ contentHtml, backgroundColor, scrollSpeed }: AnnouncementPreviewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const [animationDuration, setAnimationDuration] = useState(10);

  useEffect(() => {
    if (contentRef.current && containerRef.current) {
      const contentWidth = contentRef.current.scrollWidth;
      const containerWidth = containerRef.current.offsetWidth;
      const totalDistance = contentWidth + containerWidth;
      setAnimationDuration(totalDistance / Math.max(scrollSpeed, 10));
    }
  }, [contentHtml, scrollSpeed]);

  if (!contentHtml.trim()) {
    return (
      <div className="rounded-md border border-dashed border-border px-4 py-3 text-center text-xs text-muted-foreground">
        Nhập nội dung để xem trước
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <p className="text-xs font-medium text-muted-foreground">Xem trước:</p>
      <div
        ref={containerRef}
        className="relative overflow-hidden rounded-md py-2"
        style={{ backgroundColor }}
      >
        <div
          ref={contentRef}
          className="inline-block whitespace-nowrap text-sm text-white"
          style={{
            animation: `marquee ${animationDuration}s linear infinite`,
          }}
          dangerouslySetInnerHTML={{ __html: contentHtml }}
        />
        <style>{`
          @keyframes marquee {
            0% { transform: translateX(100%); }
            100% { transform: translateX(-100%); }
          }
        `}</style>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Create AnnouncementForm component**

Create `Fuexam-admin/components/admin/AnnouncementForm.tsx`:

```tsx
"use client";

import { useCallback, useState } from "react";
import { useRouter } from "next/navigation";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Color from "@tiptap/extension-color";
import TextStyle from "@tiptap/extension-text-style";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ApiError } from "@/lib/api/client";
import * as announcementApi from "@/lib/api/admin-announcements";
import { AnnouncementPreview } from "./AnnouncementPreview";
import type { AnnouncementResponse } from "@/types/api";

interface AnnouncementFormProps {
  initial?: AnnouncementResponse;
}

const COLOR_PRESETS = [
  "#ef4444", "#f97316", "#eab308", "#22c55e",
  "#3b82f6", "#8b5cf6", "#ec4899", "#ffffff",
];

export function AnnouncementForm({ initial }: AnnouncementFormProps) {
  const router = useRouter();
  const isEdit = !!initial;

  const [title, setTitle] = useState(initial?.title ?? "");
  const [backgroundColor, setBackgroundColor] = useState(initial?.backgroundColor ?? "#1e40af");
  const [linkUrl, setLinkUrl] = useState(initial?.linkUrl ?? "");
  const [linkLabel, setLinkLabel] = useState(initial?.linkLabel ?? "");
  const [priority, setPriority] = useState(initial?.priority ?? 0);
  const [scrollSpeed, setScrollSpeed] = useState(initial?.scrollSpeed ?? 50);
  const [stepMinutes, setStepMinutes] = useState(
    initial ? Math.round(initial.stepSeconds / 60) : 5,
  );
  const [startAt, setStartAt] = useState(
    initial?.startAt ? initial.startAt.slice(0, 16) : "",
  );
  const [endAt, setEndAt] = useState(
    initial?.endAt ? initial.endAt.slice(0, 16) : "",
  );
  const [submitting, setSubmitting] = useState(false);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: false,
        bulletList: false,
        orderedList: false,
        blockquote: false,
        codeBlock: false,
        horizontalRule: false,
      }),
      TextStyle,
      Color,
    ],
    content: initial?.contentHtml ?? "",
  });

  const contentHtml = editor?.getHTML() ?? "";

  const formValid =
    title.trim().length > 0 &&
    contentHtml.trim().length > 0 &&
    contentHtml !== "<p></p>" &&
    startAt.length > 0 &&
    endAt.length > 0 &&
    scrollSpeed >= 10 &&
    stepMinutes >= 1;

  const buildBody = useCallback(() => ({
    title: title.trim(),
    contentHtml,
    backgroundColor,
    linkUrl: linkUrl.trim() || undefined,
    linkLabel: linkLabel.trim() || undefined,
    priority,
    scrollSpeed,
    stepSeconds: stepMinutes * 60,
    startAt: new Date(startAt).toISOString(),
    endAt: new Date(endAt).toISOString(),
  }), [title, contentHtml, backgroundColor, linkUrl, linkLabel, priority, scrollSpeed, stepMinutes, startAt, endAt]);

  const handleSubmit = async (status: string) => {
    if (!formValid) return;
    setSubmitting(true);
    try {
      if (isEdit) {
        await announcementApi.updateAnnouncement(initial.id, buildBody());
        toast.success("Đã cập nhật thông báo.");
      } else {
        await announcementApi.createAnnouncement(buildBody(), status);
        toast.success(
          status === "ACTIVE" ? "Đã kích hoạt thông báo." :
          status === "SCHEDULED" ? "Đã lên lịch thông báo." :
          "Đã lưu nháp.",
        );
      }
      router.push("/announcements");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Lưu thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">
            {isEdit ? `Sửa: ${initial.title}` : "Tạo thông báo mới"}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="title">Tên nội bộ</Label>
            <Input
              id="title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="VD: Khuyến mãi hè 2026"
              required
            />
          </div>

          <div className="space-y-2">
            <Label>Nội dung thông báo</Label>
            {editor && (
              <div className="rounded-md border border-border">
                <div className="flex items-center gap-1 border-b border-border px-2 py-1">
                  <button
                    type="button"
                    onClick={() => editor.chain().focus().toggleBold().run()}
                    className={`rounded px-2 py-1 text-xs font-bold ${editor.isActive("bold") ? "bg-primary text-primary-foreground" : "hover:bg-muted"}`}
                  >
                    B
                  </button>
                  <button
                    type="button"
                    onClick={() => editor.chain().focus().toggleItalic().run()}
                    className={`rounded px-2 py-1 text-xs italic ${editor.isActive("italic") ? "bg-primary text-primary-foreground" : "hover:bg-muted"}`}
                  >
                    I
                  </button>
                  <div className="mx-1 h-4 w-px bg-border" />
                  {COLOR_PRESETS.map((color) => (
                    <button
                      key={color}
                      type="button"
                      onClick={() => editor.chain().focus().setColor(color).run()}
                      className="h-5 w-5 rounded-full border border-border"
                      style={{ backgroundColor: color }}
                      title={color}
                    />
                  ))}
                  <input
                    type="color"
                    className="h-5 w-5 cursor-pointer"
                    onChange={(e) => editor.chain().focus().setColor(e.target.value).run()}
                    title="Chọn màu khác"
                  />
                </div>
                <EditorContent
                  editor={editor}
                  className="prose prose-sm max-w-none px-3 py-2 text-foreground [&_.ProseMirror]:min-h-[60px] [&_.ProseMirror]:outline-none"
                />
              </div>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="bgColor">Màu nền</Label>
            <div className="flex items-center gap-2">
              <input
                type="color"
                id="bgColor"
                value={backgroundColor}
                onChange={(e) => setBackgroundColor(e.target.value)}
                className="h-8 w-10 cursor-pointer rounded border border-border"
              />
              <Input
                value={backgroundColor}
                onChange={(e) => setBackgroundColor(e.target.value)}
                className="w-28"
                maxLength={9}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="linkUrl">Link URL (tuỳ chọn)</Label>
              <Input
                id="linkUrl"
                value={linkUrl}
                onChange={(e) => setLinkUrl(e.target.value)}
                placeholder="https://..."
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="linkLabel">Label link</Label>
              <Input
                id="linkLabel"
                value={linkLabel}
                onChange={(e) => setLinkLabel(e.target.value)}
                placeholder="Xem ngay"
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-2">
              <Label htmlFor="priority">Ưu tiên</Label>
              <Input
                id="priority"
                type="number"
                value={priority}
                onChange={(e) => setPriority(parseInt(e.target.value, 10) || 0)}
              />
              <p className="text-[10px] text-muted-foreground">Cao = hiện trước</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="scrollSpeed">Tốc độ (px/s)</Label>
              <Input
                id="scrollSpeed"
                type="number"
                min={10}
                value={scrollSpeed}
                onChange={(e) => setScrollSpeed(parseInt(e.target.value, 10) || 50)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="stepMinutes">Lặp mỗi (phút)</Label>
              <Input
                id="stepMinutes"
                type="number"
                min={1}
                value={stepMinutes}
                onChange={(e) => setStepMinutes(parseInt(e.target.value, 10) || 5)}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="startAt">Bắt đầu</Label>
              <Input
                id="startAt"
                type="datetime-local"
                value={startAt}
                onChange={(e) => setStartAt(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="endAt">Kết thúc</Label>
              <Input
                id="endAt"
                type="datetime-local"
                value={endAt}
                onChange={(e) => setEndAt(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="flex gap-2 pt-2">
            {isEdit ? (
              <Button onClick={() => handleSubmit("DRAFT")} disabled={submitting || !formValid}>
                {submitting ? "Đang lưu..." : "Cập nhật"}
              </Button>
            ) : (
              <>
                <Button variant="outline" onClick={() => handleSubmit("DRAFT")} disabled={submitting || !formValid}>
                  Lưu nháp
                </Button>
                <Button variant="secondary" onClick={() => handleSubmit("SCHEDULED")} disabled={submitting || !formValid}>
                  Lên lịch
                </Button>
                <Button onClick={() => handleSubmit("ACTIVE")} disabled={submitting || !formValid}>
                  Kích hoạt ngay
                </Button>
              </>
            )}
            <Button variant="ghost" onClick={() => router.push("/announcements")}>
              Hủy
            </Button>
          </div>
        </CardContent>
      </Card>

      <div>
        <AnnouncementPreview
          contentHtml={contentHtml}
          backgroundColor={backgroundColor}
          scrollSpeed={scrollSpeed}
        />
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Create the "new" page**

Create `Fuexam-admin/app/announcements/new/page.tsx`:

```tsx
import { AdminShell } from "@/components/admin/AdminShell";
import { AnnouncementForm } from "@/components/admin/AnnouncementForm";

export default function NewAnnouncementPage() {
  return (
    <AdminShell title="Tạo thông báo mới" description="Soạn thông báo marquee hiển thị toàn server">
      <AnnouncementForm />
    </AdminShell>
  );
}
```

- [ ] **Step 5: Create the "edit" page**

Create `Fuexam-admin/app/announcements/[id]/edit/page.tsx`:

```tsx
"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { AdminShell } from "@/components/admin/AdminShell";
import { AnnouncementForm } from "@/components/admin/AnnouncementForm";
import { ApiError } from "@/lib/api/client";
import * as announcementApi from "@/lib/api/admin-announcements";
import type { AnnouncementResponse } from "@/types/api";

export default function EditAnnouncementPage() {
  const { id } = useParams<{ id: string }>();
  const [data, setData] = useState<AnnouncementResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    announcementApi
      .getAnnouncement(id)
      .then(setData)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Không tải được thông báo."),
      );
  }, [id]);

  return (
    <AdminShell title="Sửa thông báo" description="">
      {error && (
        <p className="text-sm text-destructive">{error}</p>
      )}
      {!data && !error && (
        <p className="text-sm text-muted-foreground">Đang tải...</p>
      )}
      {data && <AnnouncementForm initial={data} />}
    </AdminShell>
  );
}
```

- [ ] **Step 6: Verify build**

Run: `cd Fuexam-admin && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add Fuexam-admin/app/announcements/new/page.tsx \
       Fuexam-admin/app/announcements/\[id\]/edit/page.tsx \
       Fuexam-admin/components/admin/AnnouncementForm.tsx \
       Fuexam-admin/components/admin/AnnouncementPreview.tsx \
       Fuexam-admin/package.json Fuexam-admin/package-lock.json
git commit -m "feat(admin): add announcement create/edit pages with TipTap rich text editor"
```

---

### Task 8: User Frontend — AnnouncementBar with SSE + Marquee

**Files:**
- Create: `Fuexam/hooks/use-announcement-stream.ts`
- Create: `Fuexam/hooks/use-announcement-rotation.ts`
- Create: `Fuexam/components/app/announcement-bar.tsx`
- Modify: `Fuexam/app/(app)/layout.tsx:9` — add `AnnouncementBar` before `AppHeader`

**Interfaces:**
- Consumes:
  - SSE endpoint `GET /api/v1/broadcasts/stream` — listens for `announcement.sync` events
  - REST fallback `GET /api/v1/announcements/active`
  - `AnnouncementActiveResponse` shape: `{id, contentHtml, backgroundColor, linkUrl, linkLabel, priority, scrollSpeed, stepSeconds}`
- Produces:
  - `useAnnouncementStream()` — hook returning `AnnouncementActiveResponse[]`
  - `useAnnouncementRotation(announcements)` — hook returning current announcement to display
  - `AnnouncementBar` — marquee bar component at top of page

- [ ] **Step 1: Install DOMPurify**

Run:
```bash
cd Fuexam && npm install dompurify && npm install -D @types/dompurify
```

- [ ] **Step 2: Create useAnnouncementStream hook**

Create `Fuexam/hooks/use-announcement-stream.ts`:

```typescript
"use client";

import { useEffect, useRef, useState } from "react";

interface AnnouncementItem {
  id: string;
  contentHtml: string;
  backgroundColor: string;
  linkUrl: string | null;
  linkLabel: string | null;
  priority: number;
  scrollSpeed: number;
  stepSeconds: number;
}

const SSE_URL = `${process.env.NEXT_PUBLIC_API_URL ?? ""}/api/v1/broadcasts/stream`;
const FALLBACK_URL = `${process.env.NEXT_PUBLIC_API_URL ?? ""}/api/v1/announcements/active`;

export function useAnnouncementStream(): AnnouncementItem[] {
  const [announcements, setAnnouncements] = useState<AnnouncementItem[]>([]);
  const retryTimeout = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    let es: EventSource | null = null;
    let mounted = true;

    function connect() {
      es = new EventSource(SSE_URL, { withCredentials: true });

      es.addEventListener("announcement.sync", (event: MessageEvent) => {
        try {
          const parsed = JSON.parse(event.data);
          const inner = typeof parsed.message === "string" ? JSON.parse(parsed.message) : parsed;
          if (Array.isArray(inner.announcements)) {
            setAnnouncements(inner.announcements);
          }
        } catch {
          // ignore malformed events
        }
      });

      es.onerror = () => {
        es?.close();
        if (mounted) {
          retryTimeout.current = setTimeout(connect, 5000);
        }
      };
    }

    // Fetch initial state via REST fallback, then connect SSE
    fetch(FALLBACK_URL, { credentials: "include" })
      .then((res) => res.json())
      .then((json) => {
        if (mounted && Array.isArray(json.data)) {
          setAnnouncements(json.data);
        }
      })
      .catch(() => {})
      .finally(() => {
        if (mounted) connect();
      });

    return () => {
      mounted = false;
      es?.close();
      if (retryTimeout.current) clearTimeout(retryTimeout.current);
    };
  }, []);

  return announcements;
}
```

- [ ] **Step 3: Create useAnnouncementRotation hook**

Create `Fuexam/hooks/use-announcement-rotation.ts`:

```typescript
"use client";

import { useEffect, useMemo, useRef, useState } from "react";

interface AnnouncementItem {
  id: string;
  contentHtml: string;
  backgroundColor: string;
  linkUrl: string | null;
  linkLabel: string | null;
  priority: number;
  scrollSpeed: number;
  stepSeconds: number;
}

export function useAnnouncementRotation(
  announcements: AnnouncementItem[],
): AnnouncementItem | null {
  const [index, setIndex] = useState(0);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  const sorted = useMemo(
    () => [...announcements].sort((a, b) => b.priority - a.priority),
    [announcements],
  );

  useEffect(() => {
    setIndex(0);
  }, [sorted.length]);

  useEffect(() => {
    if (sorted.length <= 1) return;

    const current = sorted[index];
    if (!current) return;

    timerRef.current = setTimeout(() => {
      setIndex((prev) => (prev + 1) % sorted.length);
    }, current.stepSeconds * 1000);

    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [index, sorted]);

  if (sorted.length === 0) return null;
  return sorted[index] ?? sorted[0];
}
```

- [ ] **Step 4: Create AnnouncementBar component**

Create `Fuexam/components/app/announcement-bar.tsx`:

```tsx
"use client";

import { useEffect, useRef, useState } from "react";
import DOMPurify from "dompurify";
import { useAnnouncementStream } from "@/hooks/use-announcement-stream";
import { useAnnouncementRotation } from "@/hooks/use-announcement-rotation";

const ALLOWED_TAGS = ["span", "strong", "em", "u", "br"];
const ALLOWED_ATTR = ["style"];

function sanitize(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
  });
}

export function AnnouncementBar() {
  const announcements = useAnnouncementStream();
  const current = useAnnouncementRotation(announcements);
  const containerRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const [duration, setDuration] = useState(10);

  useEffect(() => {
    if (contentRef.current && containerRef.current && current) {
      const contentWidth = contentRef.current.scrollWidth;
      const containerWidth = containerRef.current.offsetWidth;
      const totalDistance = contentWidth + containerWidth;
      setDuration(totalDistance / Math.max(current.scrollSpeed, 10));
    }
  }, [current]);

  if (!current) return null;

  const cleanHtml = sanitize(current.contentHtml);

  return (
    <div
      className="relative z-50 w-full overflow-hidden"
      style={{ backgroundColor: current.backgroundColor }}
    >
      <div ref={containerRef} className="relative h-8 overflow-hidden">
        {current.linkUrl ? (
          <a
            href={current.linkUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="absolute flex h-full items-center whitespace-nowrap text-sm text-white no-underline"
            style={{
              animation: `announcement-marquee ${duration}s linear infinite`,
            }}
          >
            <span ref={contentRef} dangerouslySetInnerHTML={{ __html: cleanHtml }} />
            {current.linkLabel && (
              <span className="ml-3 rounded bg-white/20 px-2 py-0.5 text-xs font-medium">
                {current.linkLabel} →
              </span>
            )}
          </a>
        ) : (
          <div
            className="absolute flex h-full items-center whitespace-nowrap text-sm text-white"
            style={{
              animation: `announcement-marquee ${duration}s linear infinite`,
            }}
          >
            <span ref={contentRef} dangerouslySetInnerHTML={{ __html: cleanHtml }} />
          </div>
        )}
      </div>
      <style>{`
        @keyframes announcement-marquee {
          0% { transform: translateX(100vw); }
          100% { transform: translateX(-100%); }
        }
      `}</style>
    </div>
  );
}
```

- [ ] **Step 5: Add AnnouncementBar to app layout**

Modify `Fuexam/app/(app)/layout.tsx` to add `AnnouncementBar` before `AppHeader`:

```tsx
import type { ReactNode } from "react";
import { AppHeader } from "@/components/app/app-header";
import { AppBackdrop } from "@/components/app/app-backdrop";
import { JoinGroupPopup } from "@/components/app/join-group-popup";
import { AnnouncementBar } from "@/components/app/announcement-bar";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative min-h-screen noise-overlay">
      <AppBackdrop />
      <AnnouncementBar />
      <AppHeader />
      <main className="mx-auto max-w-[1200px] px-4 py-8 lg:px-6">{children}</main>
      <JoinGroupPopup />
    </div>
  );
}
```

- [ ] **Step 6: Verify build**

Run: `cd Fuexam && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add Fuexam/hooks/use-announcement-stream.ts \
       Fuexam/hooks/use-announcement-rotation.ts \
       Fuexam/components/app/announcement-bar.tsx \
       Fuexam/app/\(app\)/layout.tsx \
       Fuexam/package.json Fuexam/package-lock.json
git commit -m "feat(frontend): add AnnouncementBar with SSE stream and marquee rotation"
```

---

### Task 9: End-to-End Verification

**Files:** None (testing only)

**Interfaces:**
- Consumes: All tasks above

- [ ] **Step 1: Start backend**

Run:
```bash
cd backend && docker compose up -d postgres redis
cd backend && mvn -q -pl app spring-boot:run -Dspring-boot.run.profiles=local
```
Expected: Application starts, Flyway runs V47+V48 migrations

- [ ] **Step 2: Run all backend tests**

Run: `cd backend && mvn -q test`
Expected: All tests pass

- [ ] **Step 3: Start admin frontend**

Run: `cd Fuexam-admin && npm run dev`
Expected: Dev server on port 3000

- [ ] **Step 4: Test admin flow**

1. Navigate to admin panel → sidebar shows "Thông báo toàn server" link
2. Click → announcements list page (empty)
3. Click "Tạo thông báo" → form with TipTap editor
4. Fill in: title, content with colored text, background color, dates, speed, interval
5. Verify live preview shows marquee with correct styling
6. Click "Lưu nháp" → redirects to list, shows DRAFT card
7. Click "Sửa" → edit form populated
8. Click "Kích hoạt ngay" → status changes to ACTIVE

- [ ] **Step 5: Start user frontend**

Run: `cd Fuexam && npm run dev`
Expected: Dev server on port 3336

- [ ] **Step 6: Test user flow**

1. Open user site → marquee bar appears at top with the active announcement
2. Verify colored text displays correctly
3. Verify scroll speed matches admin config
4. Create a second active announcement with different priority → verify rotation
5. Deactivate announcement from admin → marquee disappears on user side (SSE update)

- [ ] **Step 7: Commit any fixes**

If any fixes were needed during testing, commit them:
```bash
git add -A
git commit -m "fix(broadcast): end-to-end testing fixes for announcements"
```
