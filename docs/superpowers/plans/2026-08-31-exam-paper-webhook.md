# Exam Paper Webhook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a third party push one FE or PE exam paper per HTTP call into the exam module, stored as a reviewable draft with images in object storage.

**Architecture:** A `permitAll` webhook endpoint verifies an HMAC signature, persists the raw body to `exam_webhook_events`, and answers `202`. A `@Scheduled` worker then decodes inline base64 images, downloads PE resource archives from allowlisted hosts, and builds an `exam_papers` row plus its FE questions or PE items. Papers stay `draft` until an admin publishes them.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Data JPA, PostgreSQL 16 + Flyway, Jackson, JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-08-31-exam-paper-webhook-design.md`

## Global Constraints

- Java 21, Spring Boot 3.5.x, Maven multi-module. Constructor injection only. Records for DTOs.
- Flyway is the only way to change schema. Hibernate `ddl-auto` stays `validate` — an entity that disagrees with its migration breaks application startup, so column types must match exactly (hash columns are `varchar(64)`, money/marks are `numeric(6,2)` ↔ `BigDecimal`).
- No database foreign keys. Cross-table references are validated in service code.
- Soft delete via `deleted_at` wherever the column exists.
- New API paths live under `/api/v1/...`. Controllers validate and delegate; business logic lives in `application/`.
- All user-facing strings (error messages, permission descriptions) are Vietnamese. Code, identifiers, comments and commit messages are English.
- Never log raw signatures, secrets, or full payload bodies.
- Feature branch: `feat/exam-paper-webhook` (already created, based on `feat/grading-paper-bank`; migrations continue at V53).
- Build command for every task: `cd backend && mvn -q -pl exam -am test` (add `-pl app -am` when migrations change).

---

### Task 1: Migrations V53 and V54

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V53__exam_paper_bank_and_webhook.sql`
- Create: `backend/app/src/main/resources/db/migration/V54__exam_paper_webhook_permissions.sql`

**Interfaces:**
- Consumes: nothing.
- Produces: tables `exam_papers`, `exam_webhook_events`; columns `exam_fe_questions.paper_id`, `exam_pe_items.paper_id`; permission slugs `exam.paper.admin:read|update|delete|publish`, `exam.webhook.admin:read`.

- [ ] **Step 1: Write V53**

```sql
-- Paper-level grouping for FE/PE content, plus the webhook inbox that feeds it.
-- FE = Final Exam question posts; PE = Practical Exam paper + downloadable resources.
-- No DB foreign keys per project decision; references are validated in the app layer.

create table exam_papers (
    id uuid primary key,
    subject_id uuid not null,
    paper_type varchar(8) not null,
    exam_code varchar(120) not null,
    term varchar(16) null,
    retake_label varchar(64) null,
    title varchar(500) not null,
    description text null,
    duration_minutes int null,
    total_mark numeric(6,2) null,
    declared_question_count int null,
    fingerprint varchar(64) not null,
    status varchar(16) not null default 'draft',
    ingest_source varchar(64) null,
    external_paper_id varchar(64) null,
    sort_order int not null default 0,
    view_count bigint not null default 0,
    lock_version int not null default 0,
    published_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint exam_papers_type_check check (paper_type in ('FE', 'PE')),
    constraint exam_papers_status_check check (status in ('draft', 'published'))
);

create unique index ux_exam_papers_fingerprint_live
    on exam_papers (fingerprint) where deleted_at is null;
create unique index ux_exam_papers_exam_code_live
    on exam_papers (lower(exam_code)) where deleted_at is null;
create index ix_exam_papers_subject_type
    on exam_papers (subject_id, paper_type, sort_order) where deleted_at is null;
create index ix_exam_papers_status
    on exam_papers (status, created_at desc) where deleted_at is null;

create trigger trg_exam_papers_updated_at
    before update on exam_papers
    for each row execute function set_updated_at();

-- Existing content keeps working: nullable column, then backfilled below.
alter table exam_fe_questions add column paper_id uuid null;
alter table exam_pe_items     add column paper_id uuid null;

create index ix_exam_fe_questions_paper
    on exam_fe_questions (paper_id, sort_order) where deleted_at is null;
create index ix_exam_pe_items_paper
    on exam_pe_items (paper_id, sort_order) where deleted_at is null;

-- Backfill: one legacy paper per subject per type, so the read path never has to
-- handle FE questions or PE items that belong to no paper.
insert into exam_papers (
    id, subject_id, paper_type, exam_code, title, fingerprint,
    status, ingest_source, published_at, created_at, updated_at)
select gen_random_uuid(), s.id, 'FE', upper(s.code) || '_LEGACY_FE',
       'FE - chưa phân loại',
       encode(sha256(('legacy-fe:' || s.id::text)::bytea), 'hex'),
       'published', 'admin', now(), now(), now()
from exam_subjects s
where s.deleted_at is null
  and exists (select 1 from exam_fe_questions q
              where q.subject_id = s.id and q.deleted_at is null);

insert into exam_papers (
    id, subject_id, paper_type, exam_code, title, fingerprint,
    status, ingest_source, published_at, created_at, updated_at)
select gen_random_uuid(), s.id, 'PE', upper(s.code) || '_LEGACY_PE',
       'PE - chưa phân loại',
       encode(sha256(('legacy-pe:' || s.id::text)::bytea), 'hex'),
       'published', 'admin', now(), now(), now()
from exam_subjects s
where s.deleted_at is null
  and exists (select 1 from exam_pe_items i
              where i.subject_id = s.id and i.deleted_at is null);

update exam_fe_questions q
set paper_id = p.id
from exam_papers p
where p.subject_id = q.subject_id
  and p.paper_type = 'FE'
  and p.ingest_source = 'admin'
  and q.paper_id is null;

update exam_pe_items i
set paper_id = p.id
from exam_papers p
where p.subject_id = i.subject_id
  and p.paper_type = 'PE'
  and p.ingest_source = 'admin'
  and i.paper_id is null;

-- Webhook inbox. Status set matches outbox_events.
create table exam_webhook_events (
    id uuid primary key,
    client_id varchar(64) not null,
    event_id varchar(120) not null,
    event_type varchar(64) not null,
    payload_json jsonb not null,
    payload_sha256 varchar(64) not null,
    signature_valid boolean not null default false,
    status varchar(16) not null default 'pending',
    attempt_count int not null default 0,
    paper_id uuid null,
    error_code varchar(64) null,
    error_message text null,
    available_at timestamptz not null default now(),
    processed_at timestamptz null,
    created_at timestamptz not null default now(),
    constraint exam_webhook_status_check
        check (status in ('pending', 'processing', 'done', 'failed'))
);

create unique index ux_exam_webhook_events_client_event
    on exam_webhook_events (client_id, event_id);
create index ix_exam_webhook_events_pending
    on exam_webhook_events (status, available_at);
```

- [ ] **Step 2: Write V54**

```sql
-- Exam paper bank + webhook RBAC permissions (same shape as V44).
INSERT INTO permissions (slug, module, resource, action, description) VALUES
('exam.paper.admin:read', 'exam', 'paper.admin', 'read', 'Admin: xem đề FE/PE'),
('exam.paper.admin:update', 'exam', 'paper.admin', 'update', 'Admin: sửa đề FE/PE'),
('exam.paper.admin:delete', 'exam', 'paper.admin', 'delete', 'Admin: xóa đề FE/PE'),
('exam.paper.admin:publish', 'exam', 'paper.admin', 'publish', 'Admin: phát hành đề FE/PE'),
('exam.webhook.admin:read', 'exam', 'webhook.admin', 'read', 'Admin: xem log webhook nhận đề')
ON CONFLICT (slug) DO NOTHING;

UPDATE roles
SET permissions_json = permissions_json
    || '["exam.paper.admin:read","exam.webhook.admin:read"]'::jsonb
WHERE slug = 'SUB_ADMIN'
  AND NOT (permissions_json @> '["exam.paper.admin:read"]'::jsonb);

UPDATE roles
SET permissions_json = permissions_json
    || '["exam.paper.admin:read","exam.paper.admin:update","exam.paper.admin:delete","exam.paper.admin:publish","exam.webhook.admin:read"]'::jsonb
WHERE slug = 'ADMIN'
  AND NOT (permissions_json @> '["exam.paper.admin:publish"]'::jsonb);
```

- [ ] **Step 3: Apply the migrations against a real database**

```bash
cd backend && docker compose up -d postgres redis
mvn -q -DskipTests package
java -jar app/target/fuoverflow-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Do NOT use `mvn -pl app -am spring-boot:run`: `-am` makes the plugin run on the aggregator module too, which has no main class and fails immediately.

Expected: Flyway reaches version 54 and the log shows `Initialized JPA EntityManagerFactory` — that line is the proof Hibernate `validate` accepted every mapping. The `local` profile then tries to reach S3-compatible storage on `localhost:9000`; without MinIO running the context fails *after* validation with `s3CompatibleObjectStorage ... Connection refused`, which is expected and unrelated to this task. Stop the process there.

- [ ] **Step 4: Verify the schema landed**

```bash
docker exec -i fuoverflow-postgres psql -U fuoverflow -d fuoverflow -c "\d exam_papers" \
  -c "\d exam_webhook_events" \
  -c "select paper_type, count(*) from exam_papers group by paper_type;" \
  -c "select count(*) from exam_fe_questions where paper_id is null and deleted_at is null;"
```

Expected: both tables exist with the indexes above; the last count is `0` (every live FE question got a paper).

- [ ] **Step 5: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V53__exam_paper_bank_and_webhook.sql \
        backend/app/src/main/resources/db/migration/V54__exam_paper_webhook_permissions.sql
git commit -m "feat(exam): add paper bank tables, webhook inbox and permissions"
```

---

### Task 2: Ingest domain records and fingerprint

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/domain/ExamPaperType.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/domain/ExamPaperStatus.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/domain/IngestAsset.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/domain/IngestResource.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/domain/IngestQuestion.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/domain/IngestPaper.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/support/Sha256.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/support/ExamPaperFingerprint.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/support/ExamPaperFingerprintTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `ExamPaperType.FE|PE`, `ExamPaperType.fromWire(String)`, `ExamPaperType.dbValue()`
  - `ExamPaperStatus.DRAFT|PUBLISHED`, `dbValue()`, `fromDbValue(String)`
  - `IngestAsset(int sortOrder, String mimeType, long sizeBytes, String sha256, byte[] content)`
  - `IngestResource(int sortOrder, String folderLabel, String filename, String mimeType, long sizeBytes, String sha256, String sourceUrl)`
  - `IngestQuestion(String externalId, int displayNo, String questionText, Integer expectedAnswerCount, Integer chapterId, BigDecimal mark, List<IngestAsset> images, List<Long> answerOptionIds)`
  - `IngestPaper(String examCode, ExamPaperType paperType, String subjectCode, String term, String retakeLabel, String title, String description, Integer durationMinutes, BigDecimal totalMark, Integer declaredQuestionCount, String sourceSystem, String externalPaperId, List<IngestQuestion> questions, List<IngestAsset> images, List<IngestResource> resources)`
  - `Sha256.hex(byte[])`, `Sha256.hexUtf8(String)`
  - `ExamPaperFingerprint.of(IngestPaper)` → 64-char lowercase hex

- [ ] **Step 1: Write the failing test**

```java
package com.fuoverflow.exam.support;

import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.domain.IngestAsset;
import com.fuoverflow.exam.domain.IngestPaper;
import com.fuoverflow.exam.domain.IngestQuestion;
import com.fuoverflow.exam.domain.IngestResource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ExamPaperFingerprintTest {

    @Test
    void isStableWhenQuestionOrderChanges() {
        IngestQuestion a = question("111", List.of(20L, 10L));
        IngestQuestion b = question("222", List.of(30L, 40L));

        assertEquals(
                ExamPaperFingerprint.of(fePaper(List.of(a, b))),
                ExamPaperFingerprint.of(fePaper(List.of(b, a))));
    }

    @Test
    void isStableWhenAnswerOptionOrderChanges() {
        assertEquals(
                ExamPaperFingerprint.of(fePaper(List.of(question("111", List.of(10L, 20L))))),
                ExamPaperFingerprint.of(fePaper(List.of(question("111", List.of(20L, 10L))))));
    }

    @Test
    void fallsBackToImageHashesWhenAnswerOptionIdsAreAbsent() {
        String withImages = ExamPaperFingerprint.of(fePaper(List.of(question("111", List.of()))));
        assertEquals(64, withImages.length());
        assertNotEquals(
                withImages,
                ExamPaperFingerprint.of(fePaper(List.of(question("111", List.of(10L))))));
    }

    @Test
    void differsWhenExamCodeDiffers() {
        IngestPaper first = fePaper(List.of(question("111", List.of(10L))));
        IngestPaper second = new IngestPaper(
                "SCM302_SU26_FE_999999", first.paperType(), first.subjectCode(), first.term(),
                null, first.title(), null, 60, new BigDecimal("50.00"), 1,
                "eos-crawler", "999999", first.questions(), List.of(), List.of());

        assertNotEquals(ExamPaperFingerprint.of(first), ExamPaperFingerprint.of(second));
    }

    @Test
    void peFingerprintUsesImageAndResourceHashes() {
        IngestPaper paper = new IngestPaper(
                "PRJ301_SU26_PE_1", ExamPaperType.PE, "PRJ301", "SU26", null,
                "PE 1", null, null, null, null, "eos-crawler", "1",
                List.of(),
                List.of(new IngestAsset(0, "image/png", 10, "aa".repeat(32), new byte[]{1})),
                List.of(new IngestResource(0, null, "a.zip", "application/zip", 20,
                        "bb".repeat(32), "https://cdn.example.com/a.zip")));

        assertEquals(64, ExamPaperFingerprint.of(paper).length());
    }

    private static IngestPaper fePaper(List<IngestQuestion> questions) {
        return new IngestPaper(
                "SCM302_SU26_FE_553972", ExamPaperType.FE, "SCM302", "SU26", null,
                "SCM302 FE", null, 60, new BigDecimal("50.00"), questions.size(),
                "eos-crawler", "553972", questions, List.of(), List.of());
    }

    private static IngestQuestion question(String externalId, List<Long> optionIds) {
        return new IngestQuestion(
                externalId, 1, null, 1, 11001, BigDecimal.ONE,
                List.of(new IngestAsset(0, "image/png", 6853,
                        Sha256.hexUtf8("image-" + externalId), new byte[]{1, 2, 3})),
                optionIds);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperFingerprintTest`
Expected: compilation failure — `ExamPaperFingerprint`, `IngestPaper` and friends do not exist.

- [ ] **Step 3: Write the domain records**

`ExamPaperType.java`:

```java
package com.fuoverflow.exam.domain;

import com.fuoverflow.common.exception.BadRequestException;

public enum ExamPaperType {
    FE, PE;

    public String dbValue() {
        return name();
    }

    public static ExamPaperType fromWire(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID", "Thiếu paperType.");
        }
        return switch (value.trim().toUpperCase()) {
            case "FE" -> FE;
            case "PE" -> PE;
            default -> throw new BadRequestException(
                    "WEBHOOK_PAYLOAD_INVALID", "paperType không hỗ trợ: " + value);
        };
    }
}
```

`ExamPaperStatus.java`:

```java
package com.fuoverflow.exam.domain;

public enum ExamPaperStatus {
    DRAFT("draft"), PUBLISHED("published");

    private final String dbValue;

    ExamPaperStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static ExamPaperStatus fromDbValue(String value) {
        return PUBLISHED.dbValue.equals(value) ? PUBLISHED : DRAFT;
    }
}
```

The four ingest records, each in its own file, exactly as listed in **Interfaces** above. Example:

```java
package com.fuoverflow.exam.domain;

public record IngestAsset(int sortOrder, String mimeType, long sizeBytes, String sha256, byte[] content) {
}
```

- [ ] **Step 4: Write Sha256 and the fingerprint**

```java
package com.fuoverflow.exam.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Sha256 {
    private Sha256() {
    }

    public static String hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String hexUtf8(String value) {
        return hex(value.getBytes(StandardCharsets.UTF_8));
    }
}
```

```java
package com.fuoverflow.exam.support;

import com.fuoverflow.exam.domain.IngestAsset;
import com.fuoverflow.exam.domain.IngestPaper;
import com.fuoverflow.exam.domain.IngestQuestion;
import com.fuoverflow.exam.domain.IngestResource;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * Identifies a paper by the content it carries, independent of delivery order.
 *
 * <p>A third party may resend the same paper with questions or options shuffled; a fingerprint
 * match must still collapse the two deliveries onto one row, otherwise every retry creates a
 * duplicate paper for members to wade through.
 */
public final class ExamPaperFingerprint {

    private ExamPaperFingerprint() {
    }

    public static String of(IngestPaper paper) {
        List<String> units = new ArrayList<>();
        for (IngestQuestion question : paper.questions()) {
            units.add(question.externalId() + ":" + unitBody(question));
        }
        for (IngestAsset image : paper.images()) {
            units.add(image.sha256());
        }
        for (IngestResource resource : paper.resources()) {
            units.add(resource.sha256());
        }

        StringJoiner joined = new StringJoiner(";");
        new TreeSet<>(units).forEach(joined::add);
        return Sha256.hexUtf8(
                paper.examCode() + "|" + paper.paperType().dbValue() + "|" + joined);
    }

    private static String unitBody(IngestQuestion question) {
        StringJoiner parts = new StringJoiner(",");
        if (question.answerOptionIds() != null && !question.answerOptionIds().isEmpty()) {
            new TreeSet<>(question.answerOptionIds())
                    .forEach(id -> parts.add(Long.toString(id)));
            return parts.toString();
        }
        TreeSet<String> hashes = new TreeSet<>();
        for (IngestAsset image : question.images()) {
            hashes.add(image.sha256());
        }
        hashes.forEach(parts::add);
        return parts.toString();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperFingerprintTest`
Expected: PASS, 5 tests.

- [ ] **Step 6: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/domain \
        backend/exam/src/main/java/com/fuoverflow/exam/support \
        backend/exam/src/test/java/com/fuoverflow/exam/support/ExamPaperFingerprintTest.java
git commit -m "feat(exam): add ingest domain records and order-independent paper fingerprint"
```

---

### Task 3: Persistence for papers and webhook events

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperEntity.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperRepository.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamWebhookEventEntity.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamWebhookEventRepository.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamFeQuestionEntity.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPeItemEntity.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/persistence/ExamPaperEntityTest.java`

**Interfaces:**
- Consumes: `ExamPaperType`, `ExamPaperStatus` (Task 2).
- Produces:
  - `ExamPaperEntity.draft(UUID id, UUID subjectId, ExamPaperType type, String examCode, String term, String retakeLabel, String title, String description, Integer durationMinutes, BigDecimal totalMark, Integer declaredQuestionCount, String fingerprint, String ingestSource, String externalPaperId, int sortOrder, Instant now)`; `publish(Instant)`; `isPublished()`; getters; `setDeletedAt`, `setUpdatedAt`, `setTitle`, `setTerm`, `setRetakeLabel`, `setDescription`, `setDurationMinutes`, `setTotalMark`, `setDeclaredQuestionCount`, `setSortOrder`
  - `ExamPaperRepository.findByIdAndDeletedAtIsNull`, `findByFingerprintAndDeletedAtIsNull`, `findByExamCodeIgnoreCaseAndDeletedAtIsNull`, `findBySubjectIdAndStatusAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc`, `findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc`, `findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc`, `countBySubjectIdAndPaperTypeAndStatusAndDeletedAtIsNull`
  - `ExamWebhookEventEntity.received(UUID id, String clientId, String eventId, String eventType, String payloadJson, String payloadSha256, boolean signatureValid, Instant now)`; `markProcessing()`, `markDone(UUID paperId, Instant now)`, `markFailed(String code, String message, Instant now)`, `retryAt(Instant available)`, `setPayloadJson(String)`; getters
  - `ExamWebhookEventRepository.findByClientIdAndEventId`, `findByIdAndSignatureValidTrue`, `findTop20ByStatusAndAvailableAtBeforeOrderByAvailableAtAsc`
  - `ExamFeQuestionEntity.getPaperId()/setPaperId(UUID)`, `ExamPeItemEntity.getPaperId()/setPaperId(UUID)`; `ExamFeQuestionEntity.create(...)` gains a trailing `UUID paperId` parameter

- [ ] **Step 1: Write the failing test**

```java
package com.fuoverflow.exam.persistence;

import com.fuoverflow.exam.domain.ExamPaperStatus;
import com.fuoverflow.exam.domain.ExamPaperType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamPaperEntityTest {

    @Test
    void draftStartsUnpublished() {
        ExamPaperEntity paper = newDraft();

        assertEquals(ExamPaperStatus.DRAFT.dbValue(), paper.getStatus());
        assertFalse(paper.isPublished());
        assertNull(paper.getPublishedAt());
        assertEquals(ExamPaperType.FE.dbValue(), paper.getPaperType());
    }

    @Test
    void publishStampsTimestampAndStatus() {
        ExamPaperEntity paper = newDraft();
        Instant at = Instant.parse("2026-08-31T10:00:00Z");

        paper.publish(at);

        assertTrue(paper.isPublished());
        assertEquals(at, paper.getPublishedAt());
        assertEquals(ExamPaperStatus.PUBLISHED.dbValue(), paper.getStatus());
    }

    private static ExamPaperEntity newDraft() {
        return ExamPaperEntity.draft(
                UUID.randomUUID(), UUID.randomUUID(), ExamPaperType.FE,
                "SCM302_SU26_FE_553972", "SU26", null, "SCM302 FE", null,
                60, new BigDecimal("50.00"), 50, "a".repeat(64),
                "webhook:eos-crawler", "553972", 0, Instant.now());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperEntityTest`
Expected: compilation failure — `ExamPaperEntity` does not exist.

- [ ] **Step 3: Write the entities and repositories**

`ExamPaperEntity` follows the existing style of `ExamPeItemEntity`: field-per-column, `@Version` on `lock_version`, private constructor plus a static factory, no JPA relationships. Column types must mirror V53 — `paper_type`/`status` as `varchar`, `fingerprint` as `varchar(64)`, `total_mark` as `BigDecimal` mapped to `numeric(6,2)`, `view_count` as `long`.

```java
@Entity
@Table(name = "exam_papers")
public class ExamPaperEntity {
    @Id private UUID id;
    @Column(name = "subject_id", nullable = false) private UUID subjectId;
    @Column(name = "paper_type", nullable = false, length = 8) private String paperType;
    @Column(name = "exam_code", nullable = false, length = 120) private String examCode;
    @Column(name = "term", length = 16) private String term;
    @Column(name = "retake_label", length = 64) private String retakeLabel;
    @Column(name = "title", nullable = false, length = 500) private String title;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "duration_minutes") private Integer durationMinutes;
    @Column(name = "total_mark", precision = 6, scale = 2) private BigDecimal totalMark;
    @Column(name = "declared_question_count") private Integer declaredQuestionCount;
    @Column(name = "fingerprint", nullable = false, length = 64) private String fingerprint;
    @Column(name = "status", nullable = false, length = 16) private String status;
    @Column(name = "ingest_source", length = 64) private String ingestSource;
    @Column(name = "external_paper_id", length = 64) private String externalPaperId;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "view_count", nullable = false) private long viewCount;
    @Version @Column(name = "lock_version", nullable = false) private int lockVersion;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    public void publish(Instant at) {
        this.status = ExamPaperStatus.PUBLISHED.dbValue();
        this.publishedAt = at;
        this.updatedAt = at;
    }

    public boolean isPublished() {
        return ExamPaperStatus.PUBLISHED.dbValue().equals(status);
    }
    // draft(...) factory + getters/setters per the Interfaces block
}
```

`ExamWebhookEventEntity` maps `payload_json` with `@JdbcTypeCode(SqlTypes.JSON)` on a `String` field, exactly as `ExamFeQuestionEntity` does for its jsonb columns.

- [ ] **Step 4: Add paperId to the two existing entities**

Add the column field, getter and setter to `ExamFeQuestionEntity` and `ExamPeItemEntity`:

```java
@Column(name = "paper_id")
private UUID paperId;
```

Append `UUID paperId` as the last parameter of `ExamFeQuestionEntity.create(...)` and assign it. Update the single existing caller, `ExamFeQuestionAdminService.create(...)`, to pass the subject's legacy FE paper id — resolve it with
`paperRepository.findByExamCodeIgnoreCaseAndDeletedAtIsNull(subject.getCode() + "_LEGACY_FE")`, falling back to `null` when absent so hand-entry keeps working on a fresh database.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS, including the pre-existing exam tests.

- [ ] **Step 6: Verify Hibernate accepts the mapping against the real schema**

```bash
cd backend && mvn -q -DskipTests package
java -jar app/target/fuoverflow-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=local 2>&1 \
  | grep -E "Initialized JPA EntityManagerFactory|Schema-validation|SchemaManagementException"
```

Expected: `Initialized JPA EntityManagerFactory`, no `Schema-validation` line. A column-type mismatch fails there — that is the whole point of running it. The later `s3CompatibleObjectStorage` connection failure is expected without MinIO and does not affect this check.

- [ ] **Step 7: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/persistence \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamFeQuestionAdminService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/persistence/ExamPaperEntityTest.java
git commit -m "feat(exam): add paper and webhook event persistence"
```

---

### Task 4: Webhook request DTOs and payload validation

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/PaperWebhookRequest.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/PaperPayload.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/PaperSourcePayload.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/QuestionPayload.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/AssetPayload.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/ResourcePayload.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/WebhookReceiptResponse.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidator.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidatorTest.java`

**Interfaces:**
- Consumes: the ingest records from Task 2; `ExamWebhookProperties` from Task 5 is *not* needed here — size limits are passed in as plain longs.
- Produces:
  - Jackson-mapped records: `PaperWebhookRequest(String eventId, String eventType, Instant sentAt, PaperPayload paper)`; `PaperPayload(String examCode, String paperType, String subjectCode, String term, String retakeLabel, String title, String description, Integer durationMinutes, BigDecimal totalMark, Integer declaredQuestionCount, PaperSourcePayload source, List<QuestionPayload> questions, List<AssetPayload> images, List<ResourcePayload> resources)`; `PaperSourcePayload(String system, String externalPaperId, Instant capturedAt)`; `QuestionPayload(String externalId, Integer displayNo, String questionText, Integer expectedAnswerCount, Integer chapterId, BigDecimal mark, List<AssetPayload> images, List<Long> answerOptionIds)`; `AssetPayload(Integer sortOrder, String mimeType, Long sizeBytes, String sha256, String contentBase64)`; `ResourcePayload(Integer sortOrder, String folderLabel, String filename, String mimeType, Long sizeBytes, String sha256, String sourceUrl)`; `WebhookReceiptResponse(UUID receiptId, String status, boolean duplicate)`
  - `ExamWebhookPayloadValidator.validate(PaperWebhookRequest request)` → `IngestPaper`, throwing `BadRequestException`/`PayloadTooLargeException` with the spec's error codes
  - `ExamWebhookPayloadValidator.EXAM_CODE_PATTERN`

- [ ] **Step 1: Write the failing test**

```java
package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.PayloadTooLargeException;
import com.fuoverflow.exam.api.dto.webhook.AssetPayload;
import com.fuoverflow.exam.api.dto.webhook.PaperPayload;
import com.fuoverflow.exam.api.dto.webhook.PaperSourcePayload;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import com.fuoverflow.exam.api.dto.webhook.QuestionPayload;
import com.fuoverflow.exam.api.dto.webhook.ResourcePayload;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.domain.IngestPaper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamWebhookPayloadValidatorTest {

    private static final byte[] PNG = pngBytes();
    private final ExamWebhookPayloadValidator validator =
            new ExamWebhookPayloadValidator(5_242_880L, 200);

    @Test
    void acceptsFePaperAndStripsChooseMarker() {
        IngestPaper paper = validator.validate(feRequest(
                question("(Choose 2 answers)\r\n\r\nWhich two?", 2)));

        assertEquals(ExamPaperType.FE, paper.paperType());
        assertEquals("SCM302", paper.subjectCode());
        assertEquals("Which two?", paper.questions().get(0).questionText());
        assertEquals(2, paper.questions().get(0).expectedAnswerCount());
        assertEquals(1, paper.questions().get(0).displayNo());
    }

    @Test
    void keepsNullQuestionTextWhenOnlyMarkerPresent() {
        IngestPaper paper = validator.validate(feRequest(question("(Choose 1 answer)", 1)));
        assertNull(paper.questions().get(0).questionText());
    }

    @Test
    void rejectsFePaperWithoutQuestions() {
        PaperWebhookRequest request = request(payload("FE", List.of(), List.of(), List.of()));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> validator.validate(request));
        assertEquals("WEBHOOK_FE_QUESTIONS_REQUIRED", ex.getCode());
    }

    @Test
    void rejectsPePaperWithNeitherImagesNorResources() {
        PaperWebhookRequest request = request(payload("PE", List.of(), List.of(), List.of()));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> validator.validate(request));
        assertEquals("WEBHOOK_PE_CONTENT_REQUIRED", ex.getCode());
    }

    @Test
    void rejectsQuestionsOnPePaper() {
        PaperWebhookRequest request = request(new PaperPayload(
                "PRJ301_SU26_PE_1", "PE", "PRJ301", "SU26", null, "PE 1", null,
                null, null, null, source(),
                List.of(question("stem", 1)), List.of(asset(PNG)), List.of()));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> validator.validate(request));
        assertEquals("WEBHOOK_PAPER_TYPE_MISMATCH", ex.getCode());
    }

    @Test
    void rejectsExamCodeDisagreeingWithExplicitFields() {
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "MAE101", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 1, source(),
                List.of(question("stem", 1)), List.of(), List.of()));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> validator.validate(request));
        assertEquals("WEBHOOK_PAPER_TYPE_MISMATCH", ex.getCode());
    }

    @Test
    void acceptsExamCodeThatDoesNotFollowTheConvention() {
        PaperWebhookRequest request = request(new PaperPayload(
                "TEST_EOS_Client_278333", "FE", "TEST", null, null, "t", null,
                20, new BigDecimal("28.50"), 1, source(),
                List.of(question("stem", 1)), List.of(), List.of()));

        assertEquals("TEST", validator.validate(request).subjectCode());
    }

    @Test
    void rejectsInvalidBase64() {
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "SCM302", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 1, source(),
                List.of(new QuestionPayload("1", 1, "stem", 1, 1, BigDecimal.ONE,
                        List.of(new AssetPayload(0, "image/png", 3L, "a".repeat(64), "not-base64!!")),
                        List.of())),
                List.of(), List.of()));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> validator.validate(request));
        assertEquals("WEBHOOK_IMAGE_INVALID_BASE64", ex.getCode());
    }

    @Test
    void rejectsNonImageBytes() {
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "SCM302", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 1, source(),
                List.of(new QuestionPayload("1", 1, "stem", 1, 1, BigDecimal.ONE,
                        List.of(asset("hello world".getBytes())), List.of())),
                List.of(), List.of()));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> validator.validate(request));
        assertEquals("WEBHOOK_IMAGE_TYPE_UNSUPPORTED", ex.getCode());
    }

    @Test
    void rejectsImageOverTheLimit() {
        ExamWebhookPayloadValidator tiny = new ExamWebhookPayloadValidator(4L, 200);
        assertThrows(PayloadTooLargeException.class,
                () -> tiny.validate(feRequest(question("stem", 1))));
    }

    @Test
    void rejectsTooManyQuestions() {
        ExamWebhookPayloadValidator narrow = new ExamWebhookPayloadValidator(5_242_880L, 1);
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "SCM302", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 2, source(),
                List.of(question("a", 1), question("b", 1)), List.of(), List.of()));
        assertThrows(PayloadTooLargeException.class, () -> narrow.validate(request));
    }

    @Test
    void assignsDisplayNoByOrderWhenAbsent() {
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "SCM302", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 2, source(),
                List.of(new QuestionPayload("a", null, "stem", 1, null, null,
                                List.of(asset(PNG)), List.of()),
                        new QuestionPayload("b", null, "stem", 1, null, null,
                                List.of(asset(PNG)), List.of())),
                List.of(), List.of()));

        IngestPaper paper = validator.validate(request);
        assertEquals(1, paper.questions().get(0).displayNo());
        assertEquals(2, paper.questions().get(1).displayNo());
    }

    private static PaperWebhookRequest feRequest(QuestionPayload question) {
        return request(payload("FE", List.of(question), List.of(), List.of()));
    }

    private static PaperWebhookRequest request(PaperPayload paper) {
        return new PaperWebhookRequest(
                "3f2b9c14-8f0e-4a51-9b77-1c6d5e8a0f21", "exam.paper.upserted",
                Instant.parse("2026-08-31T03:54:59Z"), paper);
    }

    private static PaperPayload payload(String type, List<QuestionPayload> questions,
                                        List<AssetPayload> images, List<ResourcePayload> resources) {
        return new PaperPayload(
                "FE".equals(type) ? "SCM302_SU26_FE_553972" : "PRJ301_SU26_PE_1",
                type, "FE".equals(type) ? "SCM302" : "PRJ301", "SU26", null,
                "paper title", null, 60, new BigDecimal("50.00"),
                questions.isEmpty() ? null : questions.size(),
                source(), questions, images, resources);
    }

    private static PaperSourcePayload source() {
        return new PaperSourcePayload("eos-crawler", "553972",
                Instant.parse("2026-07-26T03:54:59Z"));
    }

    private static QuestionPayload question(String text, int expected) {
        return new QuestionPayload("1920809737", 1, text, expected, 11001,
                BigDecimal.ONE, List.of(asset(PNG)), List.of(1L, 2L, 3L, 4L));
    }

    private static AssetPayload asset(byte[] content) {
        return new AssetPayload(0, "image/png", (long) content.length,
                com.fuoverflow.exam.support.Sha256.hex(content),
                Base64.getEncoder().encodeToString(content));
    }

    /** Minimal 1x1 PNG: enough for a magic-byte check. */
    private static byte[] pngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/AF+AVsAAAAASUVORK5CYII=");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookPayloadValidatorTest`
Expected: compilation failure — the DTOs, `PayloadTooLargeException` and the validator do not exist.

- [ ] **Step 3: Add PayloadTooLargeException to common**

```java
package com.fuoverflow.common.exception;

import org.springframework.http.HttpStatus;

public class PayloadTooLargeException extends ApiException {
    public PayloadTooLargeException(String code, String message) {
        super(code, message, HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
```

- [ ] **Step 4: Write the DTO records**

One record per file, fields exactly as the **Interfaces** block lists. All are plain Jackson-mapped records with no validation annotations — validation is the validator's job, so a bad payload produces a stable domain error code rather than a generic field error.

- [ ] **Step 5: Write the validator**

Behaviour, in order:

1. `paper` must be present; `eventId` non-blank (`WEBHOOK_PAYLOAD_INVALID`).
2. `paperType` via `ExamPaperType.fromWire`; `subjectCode` non-blank (`WEBHOOK_SUBJECT_CODE_REQUIRED`).
3. If `examCode` matches `EXAM_CODE_PATTERN` = `^(?<subject>\w+)_(?<term>[A-Z]{2}\d{2})_(?<type>FE|PE|PT|MID)_(?<id>\d+)$`, its captured subject/term/type must equal the explicit fields, case-insensitively for subject; otherwise `WEBHOOK_PAPER_TYPE_MISMATCH`. Non-matching codes skip the check entirely.
4. FE: `questions` non-empty (`WEBHOOK_FE_QUESTIONS_REQUIRED`); `images`/`resources` must be empty (`WEBHOOK_PAPER_TYPE_MISMATCH`). PE: at least one image or resource (`WEBHOOK_PE_CONTENT_REQUIRED`); `questions` must be empty (`WEBHOOK_PAPER_TYPE_MISMATCH`).
5. Question count over `maxQuestions` → `PayloadTooLargeException("WEBHOOK_TOO_LARGE", …)`.
6. Each asset: decode base64 (`IllegalArgumentException` → `WEBHOOK_IMAGE_INVALID_BASE64`); decoded length over `maxImageBytes` → `WEBHOOK_TOO_LARGE`; magic bytes must identify PNG/JPEG/GIF/WEBP (`WEBHOOK_IMAGE_TYPE_UNSUPPORTED`); the resolved mime type comes from the magic bytes, never from `mimeType`; `sha256` is recomputed from the decoded bytes and the declared value ignored (it is only a transport checksum for resources, which arrive by URL).
7. `questionText`: strip a leading `(Choose N answer(s))` with `Pattern.compile("^\\(Choose\\s+(\\d+)\\s+answers?\\)\\s*", CASE_INSENSITIVE)`, normalise `\r\n` to `\n`, trim, and use `null` when nothing remains. When `expectedAnswerCount` is absent, take it from the marker.
8. `displayNo` defaults to the 1-based position in the list.
9. Resources are carried through unchanged except `sortOrder` defaulting to position; `sourceUrl` is not fetched here (Task 8) but must be non-blank, and `sha256` must be 64 hex characters (`WEBHOOK_PAYLOAD_INVALID`).

Constructor: `ExamWebhookPayloadValidator(long maxImageBytes, int maxQuestions)`, plus a Spring-facing constructor that reads both from `ExamWebhookProperties` (added in Task 5 — until then use the two-arg constructor and mark the class `@Component` in Task 5).

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookPayloadValidatorTest`
Expected: PASS, 12 tests.

- [ ] **Step 7: Commit**

```bash
git add backend/common/src/main/java/com/fuoverflow/common/exception/PayloadTooLargeException.java \
        backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidator.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidatorTest.java
git commit -m "feat(exam): validate webhook paper payloads into ingest records"
```

---

### Task 5: Signature verification and webhook properties

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/config/ExamWebhookProperties.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookSignatureVerifier.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/config/ExamModuleConfig.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidator.java` (add `@Component` + properties constructor)
- Modify: `backend/app/src/main/resources/application.yml`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookSignatureVerifierTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `ExamWebhookProperties(Map<String,String> clients, List<String> allowedResourceHosts, Long maxPayloadBytes, Long maxImageBytes, Long maxResourceBytes, Integer maxQuestions, Integer maxAttempts, Integer signatureToleranceSeconds, Long pollIntervalMs)` with `*OrDefault()` accessors: payload 33_554_432; image 5_242_880; resource 52_428_800; questions 200; attempts 5; tolerance 300; poll 30_000
  - `ExamWebhookSignatureVerifier.verify(String clientId, String rawBody, String signatureHeader)` → void, throws `UnauthorizedException`
  - `ExamWebhookSignatureVerifier.sign(String secret, long timestamp, String rawBody)` → hex (test helper, package-visible is fine but keep it public for the test)

- [ ] **Step 1: Write the failing test**

```java
package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamWebhookSignatureVerifierTest {

    private static final String SECRET = "s3cr3t-for-eos-crawler";
    private static final String BODY = "{\"eventId\":\"abc\"}";

    private final ExamWebhookSignatureVerifier verifier = new ExamWebhookSignatureVerifier(
            new ExamWebhookProperties(Map.of("eos-crawler", SECRET),
                    List.of("cdn.example.com"), null, null, null, null, null, null, null));

    @Test
    void acceptsAFreshValidSignature() {
        long now = Instant.now().getEpochSecond();
        String header = "t=" + now + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, now, BODY);

        assertDoesNotThrow(() -> verifier.verify("eos-crawler", BODY, header));
    }

    @Test
    void rejectsATamperedBody() {
        long now = Instant.now().getEpochSecond();
        String header = "t=" + now + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, now, BODY);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> verifier.verify("eos-crawler", BODY + " ", header));
        assertEquals("WEBHOOK_SIGNATURE_INVALID", ex.getCode());
    }

    @Test
    void rejectsAnExpiredTimestamp() {
        long stale = Instant.now().getEpochSecond() - 301;
        String header = "t=" + stale + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, stale, BODY);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> verifier.verify("eos-crawler", BODY, header));
        assertEquals("WEBHOOK_SIGNATURE_EXPIRED", ex.getCode());
    }

    @Test
    void rejectsAnUnknownClient() {
        long now = Instant.now().getEpochSecond();
        String header = "t=" + now + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, now, BODY);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> verifier.verify("someone-else", BODY, header));
        assertEquals("WEBHOOK_CLIENT_UNKNOWN", ex.getCode());
    }

    @Test
    void rejectsAMalformedHeader() {
        assertThrows(UnauthorizedException.class,
                () -> verifier.verify("eos-crawler", BODY, "v1=deadbeef"));
        assertThrows(UnauthorizedException.class,
                () -> verifier.verify("eos-crawler", BODY, null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookSignatureVerifierTest`
Expected: compilation failure — verifier and properties do not exist.

- [ ] **Step 3: Write the properties record**

A `@ConfigurationProperties(prefix = "fuexam.exam.webhook")` record with the fields and defaults from **Interfaces**. Register it: `@EnableConfigurationProperties({ExamProperties.class, ExamWebhookProperties.class})` in `ExamModuleConfig`.

- [ ] **Step 4: Write the verifier**

`sign(secret, t, body)` is `HmacSHA256` over `t + "." + body`, hex-encoded — the same construction as `ExamMediaTokenService`. `verify` parses `t=<digits>,v1=<hex>` (order-independent, tolerant of spaces), rejects a missing/garbled header with `WEBHOOK_SIGNATURE_MALFORMED`, an unmapped `clientId` with `WEBHOOK_CLIENT_UNKNOWN`, `|now - t| > tolerance` with `WEBHOOK_SIGNATURE_EXPIRED`, and a mismatch with `WEBHOOK_SIGNATURE_INVALID`. Comparison is the constant-time loop, not `String.equals`. Never log the header or the secret.

- [ ] **Step 5: Add configuration**

Under `fuexam.exam` in `application.yml`:

```yaml
    webhook:
      clients:
        eos-crawler: ${EXAM_WEBHOOK_SECRET_EOS_CRAWLER:dev-exam-webhook-secret-change-me}
      allowed-resource-hosts:
        - cdn.example.com
      max-payload-bytes: ${EXAM_WEBHOOK_MAX_PAYLOAD_BYTES:33554432}
      max-image-bytes: ${EXAM_WEBHOOK_MAX_IMAGE_BYTES:5242880}
      max-resource-bytes: ${EXAM_WEBHOOK_MAX_RESOURCE_BYTES:52428800}
      max-questions: ${EXAM_WEBHOOK_MAX_QUESTIONS:200}
      max-attempts: ${EXAM_WEBHOOK_MAX_ATTEMPTS:5}
      signature-tolerance-seconds: ${EXAM_WEBHOOK_SIGNATURE_TOLERANCE:300}
      poll-interval-ms: ${EXAM_WEBHOOK_POLL_INTERVAL_MS:30000}
```

Also raise the multipart/request body ceiling if `spring.servlet.multipart` limits apply to the JSON body — they do not, but `server.max-http-request-header-size` is unrelated; no change needed beyond the above.

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/config \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookSignatureVerifier.java \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidator.java \
        backend/app/src/main/resources/application.yml \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookSignatureVerifierTest.java
git commit -m "feat(exam): verify HMAC-signed webhook requests"
```

---

### Task 6: Webhook endpoint and receipt persistence

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/ExamWebhookController.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookReceiptService.java`
- Modify: `backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java:96`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookReceiptServiceTest.java`

**Interfaces:**
- Consumes: `ExamWebhookSignatureVerifier`, `ExamWebhookPayloadValidator`, `ExamWebhookEventRepository`, `ExamWebhookProperties`, `Sha256`.
- Produces: `ExamWebhookReceiptService.receive(String clientId, String rawBody, String signatureHeader)` → `WebhookReceiptResponse`; `ExamWebhookController` at `POST /api/v1/exam/webhook/papers` returning `202` (new) or `200` (duplicate).

- [ ] **Step 1: Write the failing test**

```java
package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.PayloadTooLargeException;
import com.fuoverflow.exam.api.dto.webhook.WebhookReceiptResponse;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import com.fuoverflow.exam.persistence.ExamWebhookEventEntity;
import com.fuoverflow.exam.persistence.ExamWebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamWebhookReceiptServiceTest {

    private static final String SECRET = "s3cr3t";

    @Mock private ExamWebhookEventRepository eventRepository;

    private ExamWebhookReceiptService service;
    private String body;
    private String header;

    @BeforeEach
    void setUp() {
        ExamWebhookProperties properties = new ExamWebhookProperties(
                Map.of("eos-crawler", SECRET), List.of("cdn.example.com"),
                null, null, null, null, null, null, null);
        service = new ExamWebhookReceiptService(
                new ExamWebhookSignatureVerifier(properties),
                new ExamWebhookPayloadValidator(5_242_880L, 200),
                eventRepository, properties, new ObjectMapper());
        body = """
                {"eventId":"evt-1","eventType":"exam.paper.upserted",
                 "sentAt":"2026-08-31T03:54:59Z",
                 "paper":{"examCode":"TEST_EOS_Client_1","paperType":"FE","subjectCode":"TEST",
                 "title":"t","questions":[{"externalId":"1","questionText":"stem",
                 "images":[{"sortOrder":0,"mimeType":"image/png","sizeBytes":70,
                 "sha256":"%s","contentBase64":"%s"}]}]}}
                """.formatted("a".repeat(64), PNG_BASE64);
        long now = Instant.now().getEpochSecond();
        header = "t=" + now + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, now, body);
    }

    @Test
    void storesANewEventAsPending() {
        when(eventRepository.findByClientIdAndEventId("eos-crawler", "evt-1"))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookReceiptResponse response = service.receive("eos-crawler", body, header);

        assertFalse(response.duplicate());
        assertEquals("queued", response.status());
        verify(eventRepository).save(any(ExamWebhookEventEntity.class));
    }

    @Test
    void returnsTheEarlierReceiptForADuplicateEventId() {
        ExamWebhookEventEntity existing = ExamWebhookEventEntity.received(
                java.util.UUID.randomUUID(), "eos-crawler", "evt-1", "exam.paper.upserted",
                "{}", "b".repeat(64), true, Instant.now());
        when(eventRepository.findByClientIdAndEventId("eos-crawler", "evt-1"))
                .thenReturn(Optional.of(existing));

        WebhookReceiptResponse response = service.receive("eos-crawler", body, header);

        assertTrue(response.duplicate());
        assertEquals(existing.getId(), response.receiptId());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void rejectsABodyOverTheConfiguredCeiling() {
        ExamWebhookProperties tiny = new ExamWebhookProperties(
                Map.of("eos-crawler", SECRET), List.of(), 10L, null, null, null, null, null, null);
        ExamWebhookReceiptService small = new ExamWebhookReceiptService(
                new ExamWebhookSignatureVerifier(tiny),
                new ExamWebhookPayloadValidator(5_242_880L, 200),
                eventRepository, tiny, new ObjectMapper());

        assertThrows(PayloadTooLargeException.class,
                () -> small.receive("eos-crawler", body, header));
    }

    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/AF+AVsAAAAASUVORK5CYII=";
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookReceiptServiceTest`
Expected: compilation failure — `ExamWebhookReceiptService` does not exist.

- [ ] **Step 3: Write the receipt service**

Order of operations matters, so keep it exactly this:

1. Reject a body longer than `maxPayloadBytesOrDefault()` (UTF-8 byte length) with `PayloadTooLargeException("WEBHOOK_TOO_LARGE", …)` — before any parsing, so an oversized body never reaches Jackson.
2. `signatureVerifier.verify(clientId, rawBody, signatureHeader)`.
3. Parse to `PaperWebhookRequest` (`JsonProcessingException` → `BadRequestException("WEBHOOK_PAYLOAD_INVALID", …)`).
4. `eventRepository.findByClientIdAndEventId(...)` — present means return `new WebhookReceiptResponse(existing.getId(), statusOf(existing), true)` without saving anything.
5. `payloadValidator.validate(request)` — this is where a bad payload turns into a `400`, so an unprocessable delivery is rejected at the edge rather than failing later in the worker.
6. Save `ExamWebhookEventEntity.received(...)` with `payloadSha256 = Sha256.hexUtf8(rawBody)`, `signatureValid = true`, status `pending`, and return `new WebhookReceiptResponse(saved.getId(), "queued", false)`.

`statusOf` maps the stored status to the wire words: `pending`/`processing` → `"queued"`, `done` → `"done"`, `failed` → `"failed"`.

- [ ] **Step 4: Write the controller**

```java
@RestController
@RequestMapping("/api/v1/exam/webhook")
public class ExamWebhookController {
    private final ExamWebhookReceiptService receiptService;

    public ExamWebhookController(ExamWebhookReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PostMapping("/papers")
    public ResponseEntity<ApiResponse<WebhookReceiptResponse>> receivePaper(
            @RequestHeader(value = "X-Exam-Client", required = false) String clientId,
            @RequestHeader(value = "X-Exam-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        WebhookReceiptResponse receipt = receiptService.receive(clientId, rawBody, signature);
        HttpStatus status = receipt.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.ok(receipt));
    }
}
```

`@RequestBody String` is deliberate: the signature covers the exact bytes, so the body must not be re-serialised before verification.

- [ ] **Step 5: Permit the endpoint**

In `SecurityConfig`, add `"/api/v1/exam/webhook/papers"` to the existing `permitAll()` list next to `"/api/v1/payment/payos/webhook"`.

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookReceiptServiceTest` then `mvn -q -pl app -am test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/api/ExamWebhookController.java \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookReceiptService.java \
        backend/auth/src/main/java/com/fuoverflow/auth/config/SecurityConfig.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookReceiptServiceTest.java
git commit -m "feat(exam): accept signed paper webhook deliveries"
```

---

### Task 7: Byte-based ingest storage

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamIngestStorage.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamIngestStorageTest.java`

**Interfaces:**
- Consumes: `ObjectStorage`, `BlurImageGenerator` (existing, package-private in the same package), `UploadPurpose`.
- Produces: `ExamIngestStorage.storeImage(byte[] content, String mimeType, UploadPurpose purpose, boolean withBlur)` → `StoredImage(String objectKey, String blurObjectKey)`; `ExamIngestStorage.storeResource(byte[] content, String mimeType, String filename)` → `String objectKey`.

Why this exists instead of `UploadService.upload`: that path needs a `MultipartFile`, an owning user with `exam.media.admin:create`, and it charges the 30-uploads-per-hour rate limiter — a 50-image paper would be rejected halfway through. Writing through `ObjectStorage.storeBytes` skips the `uploaded_files` bookkeeping, which is safe because `UploadService.deleteByStorageReference` deletes the object whether or not a row exists, and `markLinkedByStoragePath` is a no-op when it does not.

- [ ] **Step 1: Write the failing test**

```java
package com.fuoverflow.exam.application;

import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.material.domain.UploadPurpose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExamIngestStorageTest {

    @Mock private ObjectStorage objectStorage;

    @Test
    void storesTheImageUnderThePurposeFolderAndSidecarsABlur() {
        ExamIngestStorage storage = new ExamIngestStorage(objectStorage, new BlurImageGenerator());

        ExamIngestStorage.StoredImage stored = storage.storeImage(
                png(), "image/png", UploadPurpose.EXAM_FE_IMAGE, true);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(objectStorage, times(2)).storeBytes(any(), keys.capture(), any());
        assertTrue(stored.objectKey().startsWith("exam/fe/"));
        assertTrue(stored.objectKey().endsWith(".png"));
        assertNotNull(stored.blurObjectKey());
        assertTrue(stored.blurObjectKey().endsWith("-blur.jpg"));
        assertTrue(keys.getAllValues().contains(stored.objectKey()));
    }

    @Test
    void skipsTheBlurWhenNotRequested() {
        ExamIngestStorage storage = new ExamIngestStorage(objectStorage, new BlurImageGenerator());

        ExamIngestStorage.StoredImage stored = storage.storeImage(
                png(), "image/png", UploadPurpose.EXAM_PE_IMAGE, false);

        verify(objectStorage, times(1)).storeBytes(any(), any(), any());
        assertNull(stored.blurObjectKey());
        assertTrue(stored.objectKey().startsWith("exam/pe/"));
    }

    @Test
    void storesResourcesUnderTheResourceFolderKeepingTheExtension() {
        ExamIngestStorage storage = new ExamIngestStorage(objectStorage, new BlurImageGenerator());

        String key = storage.storeResource(new byte[]{1, 2}, "application/zip", "PE01_starter.zip");

        assertTrue(key.startsWith("exam/pe/resources/"));
        assertTrue(key.endsWith(".zip"));
        verify(objectStorage).storeBytes(any(), eq(key), eq("application/zip"));
    }

    private static byte[] png() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/AF+AVsAAAAASUVORK5CYII=");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamIngestStorageTest`
Expected: compilation failure — `ExamIngestStorage` does not exist.

- [ ] **Step 3: Write the implementation**

Object keys replicate the convention used by `ObjectStorageSupport.buildObjectKey` (which is package-private in `common`, hence the duplication): `<purpose.folder()>/<yyyy>/<MM>/<dd>/<uuid><ext>`, with the extension from `FileContentValidator.extensionFor(contentType, filename)`. The blur key is `ExamMediaService.deriveBlurKey(objectKey)`; a blur failure is logged at WARN and leaves `blurObjectKey` null, matching how `ExamMediaService.uploadWithBlur` already degrades. `BlurImageGenerator` is package-private, so `ExamIngestStorage` must live in `com.fuoverflow.exam.application`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamIngestStorageTest`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamIngestStorage.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamIngestStorageTest.java
git commit -m "feat(exam): store ingested images and resources from raw bytes"
```

---

### Task 8: PE resource fetcher

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamResourceFetcher.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamResourceFetcherTest.java`

**Interfaces:**
- Consumes: `ExamWebhookProperties`, `Sha256`.
- Produces: `ExamResourceFetcher.fetch(IngestResource resource)` → `byte[]`; throws `BadRequestException` with `WEBHOOK_RESOURCE_HOST_NOT_ALLOWED`, `WEBHOOK_RESOURCE_SCHEME_INVALID`, `WEBHOOK_RESOURCE_TOO_LARGE`, `WEBHOOK_RESOURCE_HASH_MISMATCH`, `WEBHOOK_RESOURCE_FETCH_FAILED`.

- [ ] **Step 1: Write the failing test**

Use JDK `HttpServer` on an ephemeral port so the allowlist and hash checks run against a real socket rather than a mock.

```java
package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import com.fuoverflow.exam.domain.IngestResource;
import com.fuoverflow.exam.support.Sha256;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamResourceFetcherTest {

    private static final byte[] ZIP = "PKpayload".getBytes();

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/a.zip", exchange -> {
            exchange.sendResponseHeaders(200, ZIP.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(ZIP);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void downloadsFromAnAllowlistedHostAndVerifiesTheHash() {
        byte[] bytes = fetcher(List.of("127.0.0.1"), null, true)
                .fetch(resource(baseUrl + "/a.zip", Sha256.hex(ZIP)));

        assertArrayEquals(ZIP, bytes);
    }

    @Test
    void rejectsAHostOutsideTheAllowlist() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("cdn.example.com"), null, true)
                        .fetch(resource(baseUrl + "/a.zip", Sha256.hex(ZIP))));
        assertEquals("WEBHOOK_RESOURCE_HOST_NOT_ALLOWED", ex.getCode());
    }

    @Test
    void rejectsANonHttpsUrlWhenPlainHttpIsNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("127.0.0.1"), null, false)
                        .fetch(resource(baseUrl + "/a.zip", Sha256.hex(ZIP))));
        assertEquals("WEBHOOK_RESOURCE_SCHEME_INVALID", ex.getCode());
    }

    @Test
    void rejectsAHashMismatch() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("127.0.0.1"), null, true)
                        .fetch(resource(baseUrl + "/a.zip", "c".repeat(64))));
        assertEquals("WEBHOOK_RESOURCE_HASH_MISMATCH", ex.getCode());
    }

    @Test
    void rejectsABodyOverTheSizeCeiling() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("127.0.0.1"), 2L, true)
                        .fetch(resource(baseUrl + "/a.zip", Sha256.hex(ZIP))));
        assertEquals("WEBHOOK_RESOURCE_TOO_LARGE", ex.getCode());
    }

    private ExamResourceFetcher fetcher(List<String> hosts, Long maxBytes, boolean allowPlainHttp) {
        ExamWebhookProperties properties = new ExamWebhookProperties(
                Map.of(), hosts, null, null, maxBytes, null, null, null, null);
        return new ExamResourceFetcher(properties, allowPlainHttp);
    }

    private static IngestResource resource(String url, String sha256) {
        return new IngestResource(0, null, "a.zip", "application/zip", ZIP.length, sha256, url);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamResourceFetcherTest`
Expected: compilation failure — `ExamResourceFetcher` does not exist.

- [ ] **Step 3: Write the implementation**

Constructor `ExamResourceFetcher(ExamWebhookProperties properties)` for Spring plus `ExamResourceFetcher(ExamWebhookProperties properties, boolean allowPlainHttp)` for the test; the Spring one passes `false`. Using `java.net.http.HttpClient` with `followRedirects(NEVER)` — a redirect is reported as `WEBHOOK_RESOURCE_FETCH_FAILED` rather than followed, which is what keeps an allowlisted host from bouncing the request to an internal address. Checks in order: scheme (`https`, or `http` only when `allowPlainHttp`), host in `allowedResourceHosts` (exact, case-insensitive), resolved address is not loopback/link-local/site-local/any-local (skipped when `allowPlainHttp`, so the test can use `127.0.0.1`), `Content-Length` and the actual byte count both under `maxResourceBytesOrDefault()`, then `Sha256.hex(bytes)` equals the declared hash. Connect and read timeouts of 10s and 60s.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamResourceFetcherTest`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamResourceFetcher.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamResourceFetcherTest.java
git commit -m "feat(exam): fetch PE resources from allowlisted hosts with hash verification"
```

---

### Task 9: Paper ingest service

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java`

**Interfaces:**
- Consumes: everything from Tasks 2, 3, 7, 8 plus `ExamSubjectRepository`, `ExamFeQuestionRepository`, `ExamPeItemRepository`, `ExamPeResourceRepository`, `ExamMediaService`, `ObjectMapper`.
- Produces: `ExamPaperIngestService.ingest(IngestPaper paper, String ingestSource)` → `IngestOutcome(UUID paperId, Outcome outcome)` where `Outcome` is `CREATED`, `UPDATED`, or `SKIPPED_PUBLISHED`.

- [ ] **Step 1: Write the failing test**

Cover, one test each: creates a subject when the code is unknown (and leaves it inactive); creates a draft paper with one FE question per ingest question, each carrying its uploaded image key and blur key; a second ingest with the same fingerprint updates rather than duplicates; a published paper is skipped; a storage failure mid-way deletes the draft and the already-uploaded objects; a PE paper creates one `exam_pe_items` row plus its `exam_pe_resources`.

```java
@Test
void createsAnInactiveSubjectWhenTheCodeIsUnknown() {
    when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("SCM302"))
            .thenReturn(Optional.empty());
    when(subjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    // ... paperRepository/feQuestionRepository stubs

    service.ingest(fePaper(1), "webhook:eos-crawler");

    ArgumentCaptor<ExamSubjectEntity> captor = ArgumentCaptor.forClass(ExamSubjectEntity.class);
    verify(subjectRepository).save(captor.capture());
    assertFalse(captor.getValue().isActive());
    assertEquals("SCM302", captor.getValue().getCode());
}

@Test
void skipsAPaperThatIsAlreadyPublished() {
    ExamPaperEntity published = draftPaper();
    published.publish(Instant.now());
    when(paperRepository.findByFingerprintAndDeletedAtIsNull(any()))
            .thenReturn(Optional.of(published));

    ExamPaperIngestService.IngestOutcome outcome =
            service.ingest(fePaper(1), "webhook:eos-crawler");

    assertEquals(ExamPaperIngestService.Outcome.SKIPPED_PUBLISHED, outcome.outcome());
    verify(feQuestionRepository, never()).save(any());
}

@Test
void deletesTheDraftAndUploadedObjectsWhenStorageFails() {
    // second storeImage call throws; assert mediaService.deletePairedAll saw the first key
    // and paperRepository.delete(...) (hard delete of the never-published draft) was called
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperIngestServiceTest`
Expected: compilation failure — `ExamPaperIngestService` does not exist.

- [ ] **Step 3: Write the implementation**

`@Transactional` method, steps in order:

1. Resolve the subject by `subjectCode` (case-insensitive, live). Absent → create `ExamSubjectEntity` with `code = subjectCode`, `title = subjectCode`, `active = false`, `fePreviewImageCount` from `ExamProperties.defaultFePreviewImageCountOrDefault()`.
2. `fingerprint = ExamPaperFingerprint.of(paper)`. Look it up: a live published match returns `SKIPPED_PUBLISHED` immediately; a live draft match is reused (its FE questions / PE items are soft-deleted and their objects deleted before rebuilding, so an update never leaves stale questions behind); otherwise create a new draft with `sortOrder = count of live papers for (subject, type)`.
3. FE: for each `IngestQuestion`, for each image call `ingestStorage.storeImage(content, mimeType, UploadPurpose.EXAM_FE_IMAGE, true)`; serialise the collected keys with `ExamJsonUtil.serialize` into `question_image_urls` / `question_blur_urls`; save an `ExamFeQuestionEntity` with `sortOrder = displayNo - 1` and `paperId`.
4. PE: create one `ExamPeItemEntity` for the paper (title/description from the paper) with `exam_image_urls` from `storeImage(..., EXAM_PE_IMAGE, false)`; for each resource `resourceFetcher.fetch(...)` then `ingestStorage.storeResource(...)` then save an `ExamPeResourceEntity`.
5. Wrap 3–4 in a try/catch: on any exception, delete every object key stored so far via `mediaService.deletePairedAll(imageKeys, blurKeys)`, hard-delete the draft paper when this call created it, and rethrow. A half-built paper must not survive.
6. Return `CREATED` or `UPDATED`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperIngestServiceTest`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java
git commit -m "feat(exam): build draft papers from ingested webhook payloads"
```

---

### Task 10: Ingest worker

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookIngestWorker.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookIngestWorkerTest.java`

**Interfaces:**
- Consumes: `ExamWebhookEventRepository`, `ExamWebhookPayloadValidator`, `ExamPaperIngestService`, `ExamWebhookProperties`, `ObjectMapper`.
- Produces: `ExamWebhookIngestWorker.processPending()` — the `@Scheduled` entry point, also called directly by tests.

- [ ] **Step 1: Write the failing test**

```java
@Test
void marksTheEventDoneAndStripsBase64FromTheStoredPayload() {
    // given a pending event whose payload has contentBase64
    worker.processPending();
    // then status = done, paperId set, and payload_json no longer contains "contentBase64":"iVBOR
    assertFalse(event.getPayloadJson().contains("iVBORw0KGgo"));
    assertTrue(event.getPayloadJson().contains("\"sha256\""));
}

@Test
void reschedulesWithBackoffOnATransientFailure() {
    when(ingestService.ingest(any(), any())).thenThrow(new IllegalStateException("storage down"));
    worker.processPending();
    assertEquals("pending", event.getStatus());
    assertEquals(1, event.getAttemptCount());
    assertTrue(event.getAvailableAt().isAfter(Instant.now()));
}

@Test
void failsPermanentlyOnAPayloadError() {
    when(ingestService.ingest(any(), any()))
            .thenThrow(new BadRequestException("WEBHOOK_IMAGE_INVALID_BASE64", "bad"));
    worker.processPending();
    assertEquals("failed", event.getStatus());
    assertEquals("WEBHOOK_IMAGE_INVALID_BASE64", event.getErrorCode());
}

@Test
void failsPermanentlyOnceAttemptsAreExhausted() {
    // attemptCount already at maxAttempts - 1, transient failure → status failed
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookIngestWorkerTest`
Expected: compilation failure — the worker does not exist.

- [ ] **Step 3: Write the implementation**

```java
@Scheduled(fixedDelayString = "${fuexam.exam.webhook.poll-interval-ms:30000}")
public void processPending() { … }
```

For each row from `findTop20ByStatusAndAvailableAtBeforeOrderByAvailableAtAsc("pending", Instant.now())`: `markProcessing()` and save (the claim), re-parse the stored payload, `validate`, `ingest(paper, "webhook:" + clientId)`, then `markDone(paperId, now)` and rewrite `payload_json` with every `contentBase64` set to `null` — walk the tree with Jackson's `ObjectNode.put("contentBase64", (String) null)` rather than a regex. `ApiException` subclasses are permanent failures (`markFailed(code, message, now)`); anything else is transient: increment attempts, and either `retryAt(now.plusSeconds(60L << (attempts - 1)))` back to `pending` or `markFailed("WEBHOOK_INGEST_FAILED", …)` once `attemptCount >= maxAttemptsOrDefault()`. Each row is processed in its own try/catch so one poisoned event cannot stall the queue.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamWebhookIngestWorkerTest`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookIngestWorker.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookIngestWorkerTest.java
git commit -m "feat(exam): process queued paper webhooks with backoff"
```

---

### Task 11: Paper-wide FE preview gating

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java:88-160`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java`

**Interfaces:**
- Consumes: `ExamPaperRepository` (new constructor dependency).
- Produces: unchanged public signatures; `listFeQuestions` now counts preview images cumulatively across the returned list.

- [ ] **Step 1: Write the failing test**

```java
@Test
void nonMemberSeesOnlyTheFirstImagesOfTheWholePaper() {
    // subject with fePreviewImageCount = 2, 50 questions each holding exactly 1 image
    List<ExamFeQuestionEntity> questions = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
        questions.add(questionWithImages(1));
    }
    when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
            .thenReturn(questions);
    when(accessGuard.hasActiveMembership(userId)).thenReturn(false);

    PublicFeQuestionListResponse response = service.listFeQuestions("SCM302", userId);

    long full = response.questions().stream()
            .flatMap(q -> q.images().stream())
            .filter(img -> "full".equals(img.type()))
            .count();
    assertEquals(2, full);
    assertEquals(48, response.questions().stream()
            .flatMap(q -> q.images().stream())
            .filter(img -> "blur".equals(img.type()))
            .count());
}

@Test
void memberSeesEveryImageAsFull() { /* 50 questions × 1 image → 50 full */ }

@Test
void previewSpansQuestionBoundaries() {
    // 3 questions × 2 images, previewImageCount = 3
    // → q1 both full, q2 first full second blur, q3 both blur
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamCatalogQueryServiceTest`
Expected: FAIL — `nonMemberSeesOnlyTheFirstImagesOfTheWholePaper` reports 50 full images instead of 2.

- [ ] **Step 3: Write the implementation**

Replace the per-question loop index with a running counter threaded through the mapping. `toPublicQuestion` gains an `int[] consumed` (or returns the count) so the decision becomes `isMember || consumed[0]++ < previewImageCount`. Keep `PublicFeQuestionResponse.PublicImageItem.index` as the index *within the question* — the frontend uses it to key the lightbox — while gating uses the cumulative count.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamCatalogQueryServiceTest`
Expected: PASS, including the pre-existing tests in the class.

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java
git commit -m "fix(exam): count FE preview images across the paper, not per question"
```

---

### Task 12: Public paper endpoints

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicPaperSummaryResponse.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicPaperDetailResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectDetailResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectCardResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/ExamCatalogController.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java`

**Interfaces:**
- Consumes: `ExamPaperRepository`.
- Produces:
  - `PublicPaperSummaryResponse(UUID id, String type, String term, String retakeLabel, String title, int imageCount, int resourceCount)`
  - `PublicPaperDetailResponse(UUID id, UUID subjectId, String subjectCode, String type, String term, String retakeLabel, String title, String description, List<String> imageUrls, List<PublicPeItemResponse.PublicPeResourceResponse> resources)`
  - `PublicSubjectCardResponse` renames `feQuestionCount` → `fePaperCount` (keeps `pePaperCount`)
  - `PublicSubjectDetailResponse` gains `List<PublicPaperSummaryResponse> papers` and `List<PublicSubjectCardResponse> related`, and renames `feQuestionCount` → `fePaperCount`
  - `ExamCatalogQueryService.getPaper(UUID paperId, UUID userId)` → `PublicPaperDetailResponse`
  - `GET /api/v1/exam/catalog/papers/{paperId}` with `@RequirePermission("exam.content:read")`

- [ ] **Step 1: Write the failing test**

```java
@Test
void subjectDetailListsOnlyPublishedPapers() {
    when(paperRepository.findBySubjectIdAndStatusAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(
            subjectId, "published")).thenReturn(List.of(publishedFePaper()));

    PublicSubjectDetailResponse detail = service.getDetail("SCM302", userId);

    assertEquals(1, detail.papers().size());
    assertEquals("FE", detail.papers().get(0).type());
    assertEquals("SU26", detail.papers().get(0).term());
}

@Test
void relatedSubjectsExcludeTheCurrentOne() { /* same categorySlug, max 5, current id absent */ }

@Test
void paperDetailRequiresMembership() {
    doThrow(new ForbiddenException("NO_ACTIVE_MEMBERSHIP", "…"))
            .when(accessGuard).requireActiveMembership(userId);
    assertThrows(ForbiddenException.class, () -> service.getPaper(paperId, userId));
}

@Test
void paperDetailRejectsADraft() {
    when(paperRepository.findByIdAndDeletedAtIsNull(paperId)).thenReturn(Optional.of(draftPaper()));
    assertThrows(NotFoundException.class, () -> service.getPaper(paperId, userId));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamCatalogQueryServiceTest`
Expected: compilation failure — `papers()` and `getPaper` do not exist.

- [ ] **Step 3: Write the implementation**

`getDetail` loads published papers for the subject and maps counts: FE papers count their live questions, PE papers count `exam_image_urls` entries and live resources. `related` is up to 5 other active subjects sharing `categorySlug`, current subject excluded — add `ExamSubjectRepository.findTop6ByCategorySlugAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(String)` and drop the current id from the result. `getPaper` requires membership, resolves a live *published* paper (draft → `NotFoundException("EXAM_PAPER_NOT_FOUND", …)`), then returns its PE images/resources or, for an FE paper, the paper's own image list assembled from its questions in `sortOrder`. Signed URLs come from `urlResolver.signedAll`, exactly as `toPublicPeItem` already does.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/api backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java
git commit -m "feat(exam): expose published papers on the public catalog"
```

---

### Task 13: Admin paper and receipt endpoints

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminPaperResponse.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminWebhookEventResponse.java`
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperAdminService.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/ExamAdminController.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperAdminServiceTest.java`

**Interfaces:**
- Consumes: `ExamPaperRepository`, `ExamWebhookEventRepository`, `ExamFeQuestionRepository`, `ExamPeItemRepository`, `ExamPeResourceRepository`, `ExamMediaService`, `ObjectMapper`.
- Produces:
  - `AdminPaperResponse(UUID id, UUID subjectId, String paperType, String examCode, String term, String retakeLabel, String title, String status, String ingestSource, int questionCount, int resourceCount, Instant publishedAt, Instant createdAt)`
  - `AdminWebhookEventResponse(UUID id, String clientId, String eventId, String status, int attemptCount, UUID paperId, String errorCode, String errorMessage, Instant createdAt, Instant processedAt)`
  - `ExamPaperAdminService.list(UUID subjectId, String status)`, `publish(UUID paperId)`, `delete(UUID paperId)`, `getWebhookEvent(UUID receiptId)`
  - Endpoints: `GET /api/v1/admin/exam/papers`, `POST /api/v1/admin/exam/papers/{id}/publish`, `DELETE /api/v1/admin/exam/papers/{id}`, `GET /api/v1/admin/exam/webhook-events/{id}`

- [ ] **Step 1: Write the failing test**

```java
@Test
void publishRejectsAnFePaperWithNoQuestions() {
    when(paperRepository.findByIdAndDeletedAtIsNull(paperId)).thenReturn(Optional.of(draftFe()));
    when(feQuestionRepository.countByPaperIdAndDeletedAtIsNull(paperId)).thenReturn(0L);

    BadRequestException ex = assertThrows(BadRequestException.class, () -> service.publish(paperId));
    assertEquals("EXAM_PAPER_EMPTY", ex.getCode());
}

@Test
void publishStampsPublishedAt() { /* 50 questions → status published, publishedAt non-null */ }

@Test
void publishIsIdempotent() { /* already published → no exception, publishedAt unchanged */ }

@Test
void deleteSoftDeletesThePaperItsQuestionsAndTheirObjects() {
    // asserts mediaService.deletePairedAll called with the question image and blur keys
    // and that paper/questions have deletedAt set
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperAdminServiceTest`
Expected: compilation failure — the service does not exist.

- [ ] **Step 3: Write the implementation**

Add `countByPaperIdAndDeletedAtIsNull(UUID)` and `findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID)` to `ExamFeQuestionRepository` and `ExamPeItemRepository`. `publish` refuses an FE paper with no live questions and a PE paper whose item has neither images nor resources (`EXAM_PAPER_EMPTY`), and returns quietly when the paper is already published. `delete` soft-deletes the paper, its questions/items/resources, and removes their stored objects through `ExamMediaService`, mirroring `ExamPeItemAdminService.delete`. Controller methods carry `@RequirePermission("exam.paper.admin:read"|":publish"|":delete")` and `"exam.webhook.admin:read"`, under the class-level `admin.panel:access` that `ExamAdminController` already declares.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperAdminServiceTest.java
git commit -m "feat(exam): add admin paper publish, delete and webhook receipt endpoints"
```

---

### Task 14: End-to-end ingest check with the real payloads

**Files:**
- Create: `backend/exam/src/test/resources/fixtures/fe-paper-webhook.json`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperWebhookIngestFlowTest.java`

**Interfaces:**
- Consumes: the whole chain from Tasks 4–10, wired by hand with Mockito repositories (no Spring context — the module has no integration-test harness today, and adding one is out of scope).
- Produces: nothing new.

- [ ] **Step 1: Build the fixture**

Convert the first three questions of `temp/20260726_035459_ba38f17055404c41b50d7a305c9389e2.json` into the webhook envelope. Keep the real base64 images so the magic-byte and blur paths run on genuine PNGs:

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
jq '{eventId: "fixture-evt-1", eventType: "exam.paper.upserted",
     sentAt: "2026-08-31T03:54:59Z",
     paper: {examCode: .ExamCode, paperType: "FE",
             subjectCode: (.ExamCode | split("_")[0]),
             term: (.ExamCode | split("_")[1]),
             title: (.ExamCode + " import"),
             durationMinutes: .Duration, totalMark: .Mark,
             declaredQuestionCount: 3,
             source: {system: "eos-crawler", externalPaperId: (.ExamCode | split("_")[3])},
             questions: [.GrammarQuestions[0:3][] | {
               externalId: (.QID | tostring), questionText: .Text,
               chapterId: .ChapterId, mark: .Mark,
               images: [{sortOrder: 0, mimeType: "image/png",
                         sizeBytes: .ImageSize, sha256: "",
                         contentBase64: .ImageData}],
               answerOptionIds: [.QuestionAnswers[].QAID]}]}}' \
  "temp/20260726_035459_ba38f17055404c41b50d7a305c9389e2.json" \
  > backend/exam/src/test/resources/fixtures/fe-paper-webhook.json
```

`sha256` is left empty on purpose: the validator recomputes image hashes from the decoded bytes and ignores the declared value, and this fixture proves it.

- [ ] **Step 2: Write the test**

```java
@Test
void ingestsThreeRealQuestionsIntoADraftPaperWithBlurredSidecars() {
    String body = Files.readString(Path.of(
            getClass().getResource("/fixtures/fe-paper-webhook.json").toURI()));
    long now = Instant.now().getEpochSecond();
    String header = "t=" + now + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, now, body);

    WebhookReceiptResponse receipt = receiptService.receive("eos-crawler", body, header);
    worker.processPending();

    assertFalse(receipt.duplicate());
    verify(feQuestionRepository, times(3)).save(any());
    ArgumentCaptor<ExamPaperEntity> paper = ArgumentCaptor.forClass(ExamPaperEntity.class);
    verify(paperRepository, atLeastOnce()).save(paper.capture());
    assertEquals("draft", paper.getValue().getStatus());
    assertEquals("SCM302", paper.getValue().getSubjectCodeForTest()); // via the saved subject
    // 3 originals + 3 blurs
    verify(objectStorage, times(6)).storeBytes(any(), any(), any());
}

@Test
void replayingTheSameBodyDoesNotCreateASecondPaper() {
    // second receive() returns duplicate = true; worker never ingests twice
}
```

- [ ] **Step 3: Run the test**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperWebhookIngestFlowTest`
Expected: PASS, 2 tests.

- [ ] **Step 4: Commit**

```bash
git add backend/exam/src/test
git commit -m "test(exam): ingest real EOS payload through the webhook chain"
```

---

### Task 15: Frontend alignment

**Files:**
- Modify: `Fuexam/lib/api/exam.ts:191-204`
- Modify: `Fuexam/app/(app)/exam/[code]/page.tsx`

**Interfaces:**
- Consumes: the public API from Task 12.
- Produces: no new exports; `updateExamComment`/`deleteExamComment` hit the correct paths and the paper list renders.

- [ ] **Step 1: Fix the comment paths**

`updateExamComment` and `deleteExamComment` currently target `${API_V1}/exam/comments/comments/${id}`, one segment more than `ExamCommentController` maps. Drop the duplicated `comments` segment so both become `${API_V1}/exam/comments/${id}`.

- [ ] **Step 2: Remove the dead paper endpoints**

Delete `listPaperComments`, `createPaperComment`, `listPaperImageComments` and `createPaperImageComment`: the backend has no `papers` comment subject type and no `image_index` column, so all four can only 404. `PaperImages` in `exam/[code]/page.tsx` passes `paperId`/`imageIndex` into `ExamCommentThread` for exactly that dead path — switch it to the paper's own `subjectType="pe_item"` thread, or drop the side panel for PE images.

- [ ] **Step 3: Verify against a running backend**

```bash
cd backend && docker compose up -d postgres redis minio   # storage is required for image serving
mvn -q -DskipTests package && java -jar app/target/fuoverflow-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
# separate shell
cd Fuexam && npm run dev
```

Open `/exam/SCM302`: the paper list renders from `detail.papers`, the subject card shows a real number instead of `undefined đề FE`, and editing your own comment succeeds instead of returning 405.

- [ ] **Step 4: Commit**

```bash
git add Fuexam/lib/api/exam.ts "Fuexam/app/(app)/exam/[code]/page.tsx"
git commit -m "fix(exam-web): correct comment paths and drop dead paper comment endpoints"
```

---

## Self-Review

**Spec coverage:** contract → Tasks 4–6; signature → Task 5; limits → Tasks 4–5; storage tables → Task 1; entities → Task 3; fingerprint → Task 2; processing pipeline → Tasks 7–10; subject auto-create and failure cleanup → Task 9; payload stripping → Task 10; gating fix → Task 11; public API → Task 12; admin API → Task 13; permissions → Task 1 (V54); testing → each task plus Task 14; frontend consequences → Task 15. No spec section is unimplemented.

**Naming consistency:** `ExamPaperType.dbValue()` returns `"FE"`/`"PE"` (matching the `varchar(8)` check constraint) while `ExamPaperStatus.dbValue()` returns lowercase `"draft"`/`"published"` — the two differ deliberately because V53 declares them that way, and every task uses the accessor rather than a literal. `ExamWebhookEventEntity.received(...)` is the only factory; `markProcessing/markDone/markFailed/retryAt` are the only mutators. `ExamPaperFingerprint.of` is the single fingerprint entry point.

**Known deviation to flag at review:** Task 14 wires the chain with mocks rather than a Spring context, because the repository has no integration-test harness for the exam module. It therefore proves the parsing/storage/orchestration wiring, not the SQL. Task 1 Step 4 and Task 3 Step 6 are the checks that exercise the real database.
