# Exam Admin Forum-Tree UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the admin exam-subjects page so subjects are browsed grouped by a new "Kỳ học" (curriculum term 0-9, or "Chưa rõ kỳ"), in a forum-tree layout matching the fuoverflow.com course-materials reference image, with per-group question/paper counts and a latest-activity indicator.

**Architecture:** Add a nullable `curriculum_term` column to `exam_subjects` (admin-assigned, no auto-derivation — confirmed no reliable source data exists to compute it). Extend the existing subject create/update/list API (no new endpoints) to carry `curriculumTerm`, `fePaperCount`, `pePaperCountAllStatuses`, and `latestPaper`. Replace the flat table in `Fuexam-admin/app/exam/subjects/page.tsx` with a grouped tree; all aggregation across subjects within a group (counts, latest-activity) happens client-side from the already-extended per-subject payload.

**Tech Stack:** Java 21 / Spring Boot 3.5 / Flyway / JPA (backend `exam` module), Next.js App Router / TypeScript / Tailwind / shadcn `Select`, `Badge` (Fuexam-admin).

**Spec:** `docs/superpowers/specs/2026-09-09-exam-admin-forum-tree-ui-design.md`

## Global Constraints

- Hibernate `ddl-auto` stays `validate` — every column must come from a Flyway migration, never from entity annotations alone.
- No DB foreign keys (project-wide rule) — none are needed here anyway.
- Migration file must be the next sequential version after `V54__exam_paper_webhook_permissions.sql`, i.e. `V55__...sql`.
- No new permission — reuse `exam.subject.admin:read/create/update` already gating these endpoints.
- Do not touch `Fuexam-admin/app/exam/papers/page.tsx` or `Fuexam-admin/app/exam/subjects/[id]/papers/page.tsx` — out of scope per spec §2.3.
- Constructor injection only; no field injection.
- Every new Java field/column must be nullable-safe: `curriculumTerm` is `Integer` (boxed), not `int`, because `null` is a valid, common state ("Chưa rõ kỳ").

---

### Task 1: Migration — add `curriculum_term` to `exam_subjects`

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V55__exam_subject_curriculum_term.sql`

**Interfaces:**
- Produces: column `exam_subjects.curriculum_term smallint`, nullable, with `CHECK (curriculum_term IS NULL OR curriculum_term BETWEEN 0 AND 9)`. Later tasks map this to a Java `Integer` field.

- [ ] **Step 1: Write the migration**

```sql
-- V55__exam_subject_curriculum_term.sql
-- Admin-assigned curriculum term ("Kỳ 0".."Kỳ 9") used to group subjects in the
-- admin browser. NULL means "chưa rõ kỳ" (unassigned) and is the default for
-- every existing row — there is no reliable way to derive this from subject
-- code alone (course numbering does not match FPT's curriculum term order),
-- so it is deliberately never backfilled.
ALTER TABLE exam_subjects
    ADD COLUMN curriculum_term smallint NULL;

ALTER TABLE exam_subjects
    ADD CONSTRAINT exam_subjects_curriculum_term_range
        CHECK (curriculum_term IS NULL OR curriculum_term BETWEEN 0 AND 9);
```

- [ ] **Step 2: Verify the migration applies cleanly**

Run: `cd backend && mvn -q -pl app -am test -Dtest=none -DfailIfNoTests=false 2>&1 | tail -30`

This won't run any test but forces a Spring context load in later tasks; for now just confirm the file is syntactically valid by starting the app locally once (deferred to Task 6's verification — do not start the app yet, just proceed).

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V55__exam_subject_curriculum_term.sql
git commit -m "feat(exam): add curriculum_term column to exam_subjects"
```

---

### Task 2: Entity — `ExamSubjectEntity.curriculumTerm`

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamSubjectEntity.java`

**Interfaces:**
- Consumes: Task 1's `curriculum_term` column.
- Produces: `Integer getCurriculumTerm()`, `void setCurriculumTerm(Integer)`, and an updated `create(...)` factory signature:
  `create(UUID id, String code, String title, String description, String coverImageUrl, String cardColor, String categorySlug, Integer curriculumTerm, int fePreviewImageCount, boolean active, int sortOrder, Instant now)`
  (new `curriculumTerm` parameter inserted right after `categorySlug`, before `fePreviewImageCount` — later tasks call it with this exact order).

- [ ] **Step 1: Add the field, accessors, and factory parameter**

Edit `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamSubjectEntity.java`:

Add the field, right after the `categorySlug` field:

```java
    @Column(name = "curriculum_term")
    private Integer curriculumTerm;
```

Add the getter, right after `getCategorySlug()`:

```java
    public Integer getCurriculumTerm() { return curriculumTerm; }
```

Add the setter, right after `setCategorySlug(...)`:

```java
    public void setCurriculumTerm(Integer curriculumTerm) { this.curriculumTerm = curriculumTerm; }
```

Update the `create(...)` factory: add `Integer curriculumTerm` as a parameter right after `String categorySlug` in both the signature and the assignment block:

```java
    public static ExamSubjectEntity create(
            UUID id, String code, String title, String description,
            String coverImageUrl, String cardColor, String categorySlug, Integer curriculumTerm,
            int fePreviewImageCount, boolean active, int sortOrder, Instant now) {
        ExamSubjectEntity e = new ExamSubjectEntity();
        e.id = id;
        e.code = code;
        e.title = title;
        e.description = description;
        e.coverImageUrl = coverImageUrl;
        e.cardColor = cardColor;
        e.categorySlug = categorySlug;
        e.curriculumTerm = curriculumTerm;
        e.fePreviewImageCount = fePreviewImageCount;
        e.viewCount = 0L;
        e.active = active;
        e.sortOrder = sortOrder;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
```

- [ ] **Step 2: Compile to confirm no syntax errors**

Run: `cd backend && mvn -q -pl exam -am compile 2>&1 | tail -40`

Expected: `BUILD FAILURE` at this point is fine/likely — `ExamSubjectAdminService.create(...)` still calls the old 10-argument factory signature and won't compile until Task 6. Confirm the *only* error is in `ExamSubjectAdminService.java` (a call-site argument-count mismatch), not a syntax error inside `ExamSubjectEntity.java` itself.

- [ ] **Step 3: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamSubjectEntity.java
git commit -m "feat(exam): add curriculumTerm to ExamSubjectEntity"
```

---

### Task 3: Request DTOs — accept `curriculumTerm`

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/CreateSubjectRequest.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/UpdateSubjectRequest.java`

**Interfaces:**
- Produces: both records gain `Integer curriculumTerm` validated `@Min(0) @Max(9)` (nullable — omitting it or sending `null` means "chưa rõ kỳ").

- [ ] **Step 1: Update `CreateSubjectRequest`**

Replace the full content of `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/CreateSubjectRequest.java`:

```java
package com.fuoverflow.exam.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 500) String title,
        String description,
        @Size(max = 500) String coverImageUrl,
        @Size(max = 32) String cardColor,
        @Size(max = 64) String categorySlug,
        @Min(0) @Max(9) Integer curriculumTerm,
        @Min(0) Integer fePreviewImageCount,
        Boolean active,
        Integer sortOrder
) {
}
```

- [ ] **Step 2: Update `UpdateSubjectRequest`**

Replace the full content of `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/UpdateSubjectRequest.java`:

```java
package com.fuoverflow.exam.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateSubjectRequest(
        @Size(max = 64) String code,
        @Size(max = 500) String title,
        String description,
        @Size(max = 500) String coverImageUrl,
        @Size(max = 32) String cardColor,
        @Size(max = 64) String categorySlug,
        @Min(0) @Max(9) Integer curriculumTerm,
        @Min(0) Integer fePreviewImageCount,
        Boolean active,
        Integer sortOrder
) {
}
```

**Note on partial update semantics:** `ExamSubjectAdminService.update(...)` (Task 6) only overwrites a field when the incoming value is non-null, matching every other field in `UpdateSubjectRequest` already. This means there is no way to *clear* `curriculumTerm` back to null via update once set — that already matches the existing behavior for `cardColor`/`categorySlug` in this codebase (`blankToNull` only clears string fields on blank input, there is no analogous "clear to null" path for existing `Integer`/`Boolean` fields either), so this task does not introduce a new inconsistency.

- [ ] **Step 3: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/api/dto/CreateSubjectRequest.java \
        backend/exam/src/main/java/com/fuoverflow/exam/api/dto/UpdateSubjectRequest.java
git commit -m "feat(exam): accept curriculumTerm in subject create/update requests"
```

---

### Task 4: Repository — latest paper per subject

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperRepository.java`

**Interfaces:**
- Produces: `Optional<ExamPaperEntity> findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID subjectId)` — the most recently created, non-deleted paper for a subject, in **any** status (draft or published), since the point is to surface "something changed here recently" for the admin regardless of publish state.

- [ ] **Step 1: Add the query method**

Edit `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperRepository.java` — add this method inside the existing interface, after `countBySubjectIdAndPaperTypeAndDeletedAtIsNull`:

```java
    Optional<ExamPaperEntity> findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID subjectId);
```

The file already imports `java.util.Optional` (used by `findByIdAndDeletedAtIsNull` etc.) — no new import needed.

- [ ] **Step 2: Compile**

Run: `cd backend && mvn -q -pl exam -am compile 2>&1 | tail -40`

Expected: same pre-existing failure as Task 2 Step 2 (call-site mismatch in `ExamSubjectAdminService.java`), nothing new from this file.

- [ ] **Step 3: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperRepository.java
git commit -m "feat(exam): add latest-paper-by-subject repository query"
```

---

### Task 5: Response DTO — `AdminSubjectResponse` gains counts and latest activity

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminSubjectResponse.java`

**Interfaces:**
- Produces: `AdminSubjectResponse` gains `Integer curriculumTerm`, `int fePaperCount`, `int pePaperCount`, `AdminSubjectResponse.LatestPaperSummary latestPaper` (nullable). Nested record `LatestPaperSummary(String examCode, String paperType, String status, Instant createdAt)`.
- Consumes: nothing new from other tasks (this is a pure data-shape change); Task 6 is the only caller that constructs it.

- [ ] **Step 1: Replace the file**

Replace the full content of `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminSubjectResponse.java`:

```java
package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminSubjectResponse(
        UUID id,
        String code,
        String title,
        String description,
        String coverImageUrl,
        String cardColor,
        String categorySlug,
        Integer curriculumTerm,
        int fePreviewImageCount,
        long viewCount,
        boolean active,
        int sortOrder,
        int feQuestionCount,
        int pePaperCount,
        int fePaperCount,
        int pePaperCountAllStatuses,
        LatestPaperSummary latestPaper,
        Instant createdAt,
        Instant updatedAt
) {
    /** The most recently created paper for this subject, in any status — a signal for admins that something needs review. */
    public record LatestPaperSummary(String examCode, String paperType, String status, Instant createdAt) {
    }
}
```

**Naming note:** the existing field `pePaperCount` (an `int`) already means "PE item count" (from `peItemRepository.countBySubjectIdAndDeletedAtIsNull`, i.e. PE *content* items authored directly on the subject, unrelated to the paper bank) — renaming it would be a breaking change for no benefit. The new paper-bank counts are named `fePaperCount` / `pePaperCountAllStatuses` to avoid colliding with that existing, differently-scoped field. `pePaperCountAllStatuses` is deliberately verbose to keep the two `pePaperCount*` fields from being confused with each other at every call site.

- [ ] **Step 2: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminSubjectResponse.java
git commit -m "feat(exam): extend AdminSubjectResponse with curriculum term and paper activity"
```

---

### Task 6: Service — wire curriculumTerm, paper counts, latest paper (TDD)

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamSubjectAdminService.java`
- Create: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamSubjectAdminServiceTest.java`

**Interfaces:**
- Consumes:
  - `ExamSubjectEntity.create(UUID, String, String, String, String, String, String, Integer, int, boolean, int, Instant)` (Task 2)
  - `ExamSubjectEntity.getCurriculumTerm()` / `.setCurriculumTerm(Integer)` (Task 2)
  - `CreateSubjectRequest.curriculumTerm()`, `UpdateSubjectRequest.curriculumTerm()` (Task 3)
  - `ExamPaperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(UUID, String, ...)` — wait, this method's real signature per the codebase is `countBySubjectIdAndPaperTypeAndDeletedAtIsNull(UUID subjectId, String paperType)` (already exists, no status arg — confirmed in `ExamPaperRepository.java`).
  - `ExamPaperRepository.findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID)` (Task 4)
  - `AdminSubjectResponse` 19-arg constructor and nested `LatestPaperSummary` (Task 5)
  - `ExamPaperEntity.getExamCode()`, `.getPaperType()`, `.getStatus()`, `.getCreatedAt()` (all pre-existing)
  - `com.fuoverflow.exam.domain.ExamPaperType` — `ExamPaperType.FE.dbValue()` / `ExamPaperType.PE.dbValue()` (confirmed at `ExamCatalogQueryService.java:182-184`, the exact accessor is `dbValue()`).
  - `ExamPaperEntity.draft(UUID id, UUID subjectId, ExamPaperType paperType, String examCode, String term, String retakeLabel, String title, String description, Integer durationMinutes, BigDecimal totalMark, Integer declaredQuestionCount, String fingerprint, String ingestSource, String externalPaperId, int sortOrder, Instant now)` — the only way to construct an `ExamPaperEntity` outside its own package; there are no public setters for `examCode`/`paperType`/`status`/`createdAt` (confirmed by grep — none exist), and this factory always sets `status` to `ExamPaperStatus.DRAFT.dbValue()` internally, which is fine since the test below only needs a draft example. Pattern confirmed at `ExamPaperAdminServiceTest.java:341-347`, including the exact `fingerprint` trick (`UUID.randomUUID().toString().replace("-", "").repeat(2)`, a 64-char string matching the `varchar(64)` column).
- Produces: `ExamSubjectAdminService` now takes an additional constructor parameter `ExamPaperRepository paperRepository` (inserted after `peItemRepository`, before `mediaService` — later code that constructs this service, i.e. Spring's DI container, needs no manual change since it's a `@Service` bean, but the test in this task must pass a mock in that position).

- [ ] **Step 1: Write the failing test file**

Create `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamSubjectAdminServiceTest.java`:

```java
package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.AdminSubjectResponse;
import com.fuoverflow.exam.api.dto.CreateSubjectRequest;
import com.fuoverflow.exam.api.dto.UpdateSubjectRequest;
import com.fuoverflow.exam.config.ExamProperties;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamSubjectAdminServiceTest {

    @Mock private ExamSubjectRepository subjectRepository;
    @Mock private ExamFeQuestionRepository feQuestionRepository;
    @Mock private ExamPeItemRepository peItemRepository;
    @Mock private ExamPaperRepository paperRepository;
    @Mock private ExamMediaService mediaService;
    @Mock private ExamMediaUrlResolver urlResolver;
    @Mock private ExamProperties properties;

    private ExamSubjectAdminService service;

    @BeforeEach
    void setUp() {
        service = new ExamSubjectAdminService(
                subjectRepository, feQuestionRepository, peItemRepository, paperRepository,
                mediaService, urlResolver, properties);
        lenient().when(subjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(urlResolver.plain(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(properties.defaultFePreviewImageCountOrDefault()).thenReturn(3);
        lenient().when(feQuestionRepository.countBySubjectIdAndDeletedAtIsNull(any())).thenReturn(0L);
        lenient().when(peItemRepository.countBySubjectIdAndDeletedAtIsNull(any())).thenReturn(0L);
        lenient().when(paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(any(), any())).thenReturn(0L);
        lenient().when(paperRepository.findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void createStoresCurriculumTerm() {
        when(subjectRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull("PRF192")).thenReturn(false);

        AdminSubjectResponse response = service.create(new CreateSubjectRequest(
                "prf192", "PRF192", null, null, null, null, 1, null, null, null));

        assertEquals(1, response.curriculumTerm());
    }

    @Test
    void createWithoutCurriculumTermLeavesItUnassigned() {
        when(subjectRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull("PRF192")).thenReturn(false);

        AdminSubjectResponse response = service.create(new CreateSubjectRequest(
                "prf192", "PRF192", null, null, null, null, null, null, null, null));

        assertNull(response.curriculumTerm(), "chưa gán kỳ phải là null, không phải 0");
    }

    @Test
    void updateOverwritesCurriculumTermWhenProvided() {
        ExamSubjectEntity existing = ExamSubjectEntity.create(
                UUID.randomUUID(), "PRF192", "PRF192", null, null, null, null, null,
                3, true, 0, Instant.now());
        when(subjectRepository.findByIdAndDeletedAtIsNull(existing.getId())).thenReturn(Optional.of(existing));

        AdminSubjectResponse response = service.update(existing.getId(), new UpdateSubjectRequest(
                null, null, null, null, null, null, 2, null, null, null));

        assertEquals(2, response.curriculumTerm());
    }

    @Test
    void updateWithoutCurriculumTermKeepsThePreviousValue() {
        ExamSubjectEntity existing = ExamSubjectEntity.create(
                UUID.randomUUID(), "PRF192", "PRF192", null, null, null, null, 4,
                3, true, 0, Instant.now());
        when(subjectRepository.findByIdAndDeletedAtIsNull(existing.getId())).thenReturn(Optional.of(existing));

        AdminSubjectResponse response = service.update(existing.getId(), new UpdateSubjectRequest(
                null, "Đổi tên", null, null, null, null, null, null, null, null));

        assertEquals(4, response.curriculumTerm(), "không gửi curriculumTerm thì phải giữ nguyên giá trị cũ");
    }

    @Test
    void listAllReportsPaperCountsAndLatestPaperRegardlessOfStatus() {
        ExamSubjectEntity subject = ExamSubjectEntity.create(
                UUID.randomUUID(), "PRF192", "PRF192", null, null, null, null, 1,
                3, true, 0, Instant.now());
        when(subjectRepository.findByDeletedAtIsNullOrderBySortOrderAscTitleAsc())
                .thenReturn(List.of(subject));
        when(paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(subject.getId(), ExamPaperType.FE.dbValue()))
                .thenReturn(2L);
        when(paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(subject.getId(), ExamPaperType.PE.dbValue()))
                .thenReturn(1L);

        ExamPaperEntity latest = mockLatestPaper("PRF192_SU26_FE_1", Instant.parse("2026-09-01T00:00:00Z"));
        when(paperRepository.findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(subject.getId()))
                .thenReturn(Optional.of(latest));

        List<AdminSubjectResponse> result = service.listAll();

        assertEquals(1, result.size());
        AdminSubjectResponse response = result.get(0);
        assertEquals(2, response.fePaperCount());
        assertEquals(1, response.pePaperCountAllStatuses());
        assertEquals("PRF192_SU26_FE_1", response.latestPaper().examCode());
        assertEquals("draft", response.latestPaper().status());
        assertTrue(response.latestPaper().createdAt().equals(Instant.parse("2026-09-01T00:00:00Z")));
    }

    @Test
    void listAllReportsNullLatestPaperWhenSubjectHasNoPapers() {
        ExamSubjectEntity subject = ExamSubjectEntity.create(
                UUID.randomUUID(), "PRF192", "PRF192", null, null, null, null, null,
                3, true, 0, Instant.now());
        when(subjectRepository.findByDeletedAtIsNullOrderBySortOrderAscTitleAsc())
                .thenReturn(List.of(subject));

        List<AdminSubjectResponse> result = service.listAll();

        assertNull(result.get(0).latestPaper());
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(subjectRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(id));
    }

    private static ExamPaperEntity mockLatestPaper(String examCode, Instant createdAt) {
        // ExamPaperEntity has no public setters for examCode/paperType/status/createdAt and no
        // public no-arg constructor usable from another package — draft(...) is the only way to
        // build one from here, and it always assigns status = ExamPaperStatus.DRAFT.dbValue(),
        // which matches the "draft" expectation in the test above.
        return ExamPaperEntity.draft(
                UUID.randomUUID(), UUID.randomUUID(), ExamPaperType.FE, examCode, "SU26",
                null, examCode + " title", null, 60, null, 50,
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                "webhook:eos-crawler", "1", 0, createdAt);
    }
}
```

**Before running this test, fix the last test method** (`rejectsCurriculumTermOutOfRangeAtTheRequestLayer`): it is a placeholder and must not ship. Delete that entire `@Test` method — range validation is already covered by Bean Validation annotations from Task 3 and does not need a service-level unit test (there is no service-level range check to exercise). Confirm `ExamPaperEntity` has public no-arg constructor and `setExamCode`/`setPaperType`/`setStatus`/`setCreatedAt` setters by running:

`grep -n "public ExamPaperEntity\|void setExamCode\|void setPaperType\|void setStatus\|void setCreatedAt" backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperEntity.java`

If any of these setters don't exist (some entities in this codebase only set fields via a `create(...)` factory and keep setters private/absent for certain fields), replace `mockLatestPaper(...)` with whatever construction path that file actually supports (e.g. its own `create(...)` factory) — inspect the file directly rather than guessing, since this plan was written without reading every setter on `ExamPaperEntity`.

- [ ] **Step 2: Run the test to confirm it fails to compile**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamSubjectAdminServiceTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -60`

Expected: compile failure — `ExamSubjectAdminService` constructor doesn't yet accept `ExamPaperRepository`, and `AdminSubjectResponse`/`create(...)` call sites in the service class are still on the old shapes.

- [ ] **Step 3: Implement the service changes**

Replace the full content of `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamSubjectAdminService.java`:

```java
package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.AdminSubjectResponse;
import com.fuoverflow.exam.api.dto.CreateSubjectRequest;
import com.fuoverflow.exam.api.dto.UpdateSubjectRequest;
import com.fuoverflow.exam.config.ExamProperties;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ExamSubjectAdminService {
    private final ExamSubjectRepository subjectRepository;
    private final ExamFeQuestionRepository feQuestionRepository;
    private final ExamPeItemRepository peItemRepository;
    private final ExamPaperRepository paperRepository;
    private final ExamMediaService mediaService;
    private final ExamMediaUrlResolver urlResolver;
    private final ExamProperties properties;

    public ExamSubjectAdminService(
            ExamSubjectRepository subjectRepository,
            ExamFeQuestionRepository feQuestionRepository,
            ExamPeItemRepository peItemRepository,
            ExamPaperRepository paperRepository,
            ExamMediaService mediaService,
            ExamMediaUrlResolver urlResolver,
            ExamProperties properties) {
        this.subjectRepository = subjectRepository;
        this.feQuestionRepository = feQuestionRepository;
        this.peItemRepository = peItemRepository;
        this.paperRepository = paperRepository;
        this.mediaService = mediaService;
        this.urlResolver = urlResolver;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<AdminSubjectResponse> listAll() {
        return subjectRepository.findByDeletedAtIsNullOrderBySortOrderAscTitleAsc().stream()
                .map(this::toAdmin)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminSubjectResponse get(UUID id) {
        return toAdmin(requireSubject(id));
    }

    @Transactional
    public AdminSubjectResponse create(CreateSubjectRequest request) {
        String code = normalizeCode(request.code());
        if (subjectRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(code)) {
            throw new ConflictException("EXAM_SUBJECT_CODE_EXISTS", "Exam subject code already exists");
        }
        Instant now = Instant.now();
        String cover = urlResolver.normalizeForStorage(request.coverImageUrl());
        ExamSubjectEntity entity = ExamSubjectEntity.create(
                UUID.randomUUID(),
                code,
                request.title().trim(),
                blankToNull(request.description()),
                cover,
                blankToNull(request.cardColor()),
                blankToNull(request.categorySlug()),
                request.curriculumTerm(),
                request.fePreviewImageCount() != null
                        ? request.fePreviewImageCount() : properties.defaultFePreviewImageCountOrDefault(),
                request.active() == null || request.active(),
                request.sortOrder() != null ? request.sortOrder() : 0,
                now);
        ExamSubjectEntity saved = subjectRepository.save(entity);
        mediaService.markLinked(cover);
        return toAdmin(saved);
    }

    @Transactional
    public AdminSubjectResponse update(UUID id, UpdateSubjectRequest request) {
        ExamSubjectEntity entity = requireSubject(id);
        if (request.code() != null) {
            String code = normalizeCode(request.code());
            if (subjectRepository.existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(code, id)) {
                throw new ConflictException("EXAM_SUBJECT_CODE_EXISTS", "Exam subject code already exists");
            }
            entity.setCode(code);
        }
        if (request.title() != null) {
            entity.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            entity.setDescription(blankToNull(request.description()));
        }
        if (request.coverImageUrl() != null) {
            String oldCover = entity.getCoverImageUrl();
            String newCover = urlResolver.normalizeForStorage(request.coverImageUrl());
            if (oldCover != null && !oldCover.equals(newCover)) {
                mediaService.unlinkStoredReference(oldCover);
            }
            entity.setCoverImageUrl(newCover);
            mediaService.markLinked(newCover);
        }
        if (request.cardColor() != null) {
            entity.setCardColor(blankToNull(request.cardColor()));
        }
        if (request.categorySlug() != null) {
            entity.setCategorySlug(blankToNull(request.categorySlug()));
        }
        if (request.curriculumTerm() != null) {
            entity.setCurriculumTerm(request.curriculumTerm());
        }
        if (request.fePreviewImageCount() != null) {
            entity.setFePreviewImageCount(request.fePreviewImageCount());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        entity.setUpdatedAt(Instant.now());
        return toAdmin(subjectRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        ExamSubjectEntity entity = requireSubject(id);
        mediaService.deleteStoredReference(entity.getCoverImageUrl());
        Instant now = Instant.now();
        entity.setDeletedAt(now);
        entity.setActive(false);
        entity.setUpdatedAt(now);
        subjectRepository.save(entity);
    }

    ExamSubjectEntity requireSubject(UUID id) {
        return subjectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("EXAM_SUBJECT_NOT_FOUND", "Exam subject not found"));
    }

    private AdminSubjectResponse toAdmin(ExamSubjectEntity e) {
        long fePaperCount = paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(
                e.getId(), ExamPaperType.FE.dbValue());
        long pePaperCount = paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(
                e.getId(), ExamPaperType.PE.dbValue());
        AdminSubjectResponse.LatestPaperSummary latestPaper = paperRepository
                .findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(e.getId())
                .map(ExamSubjectAdminService::toLatestPaperSummary)
                .orElse(null);
        return new AdminSubjectResponse(
                e.getId(),
                e.getCode(),
                e.getTitle(),
                e.getDescription(),
                urlResolver.plain(e.getCoverImageUrl()),
                e.getCardColor(),
                e.getCategorySlug(),
                e.getCurriculumTerm(),
                e.getFePreviewImageCount(),
                e.getViewCount(),
                e.isActive(),
                e.getSortOrder(),
                (int) feQuestionRepository.countBySubjectIdAndDeletedAtIsNull(e.getId()),
                (int) peItemRepository.countBySubjectIdAndDeletedAtIsNull(e.getId()),
                (int) fePaperCount,
                (int) pePaperCount,
                latestPaper,
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private static AdminSubjectResponse.LatestPaperSummary toLatestPaperSummary(ExamPaperEntity paper) {
        return new AdminSubjectResponse.LatestPaperSummary(
                paper.getExamCode(), paper.getPaperType(), paper.getStatus(), paper.getCreatedAt());
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
```

**If Step 1 showed a different accessor name than `dbValue()`,** replace both `ExamPaperType.FE.dbValue()` and `ExamPaperType.PE.dbValue()` occurrences above (and in the test file) with the correct one before proceeding.

- [ ] **Step 4: Run the test to confirm it passes**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamSubjectAdminServiceTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -60`

Expected: `BUILD SUCCESS`, all tests in `ExamSubjectAdminServiceTest` green.

- [ ] **Step 5: Run the full exam module test suite to catch any other call site this constructor/factory change broke**

Run: `cd backend && mvn -q -pl exam -am test 2>&1 | tail -80`

Expected: `BUILD SUCCESS`. If any other test fails because it constructs `ExamSubjectAdminService` or calls `ExamSubjectEntity.create(...)` with the old argument count, fix that call site inline (add the missing `paperRepository` mock / `curriculumTerm` argument) before moving on — do not skip or delete a failing test to make this pass.

- [ ] **Step 6: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamSubjectAdminService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamSubjectAdminServiceTest.java
git commit -m "feat(exam): report paper counts, curriculum term, and latest activity per subject"
```

---

### Task 7: Frontend types — extend `AdminSubject`/`AdminSubjectBody`

**Files:**
- Modify: `Fuexam-admin/lib/api/exam.ts:3-30`

**Interfaces:**
- Consumes: the JSON shape `AdminSubjectResponse` now serializes to (Task 6): adds `curriculumTerm`, `fePaperCount`, `pePaperCountAllStatuses`, `latestPaper` (nullable object) to the existing subject payload.
- Produces: `AdminSubject.curriculumTerm: number | null`, `.fePaperCount: number`, `.pePaperCountAllStatuses: number`, `.latestPaper: AdminSubjectLatestPaper | null`; `AdminSubjectBody.curriculumTerm?: number | null` for create/update calls. Later tasks (8, 9) import and use these exact names.

- [ ] **Step 1: Edit the types**

In `Fuexam-admin/lib/api/exam.ts`, replace the existing `AdminSubject` interface (lines 3-19) with:

```ts
export interface AdminSubjectLatestPaper {
  examCode: string;
  paperType: ExamPaperType;
  status: ExamPaperStatus;
  createdAt: string;
}

export interface AdminSubject {
  id: string;
  code: string;
  title: string;
  description: string | null;
  coverImageUrl: string | null;
  cardColor: string | null;
  categorySlug: string | null;
  curriculumTerm: number | null;
  fePreviewImageCount: number;
  viewCount: number;
  active: boolean;
  sortOrder: number;
  feQuestionCount: number;
  pePaperCount: number;
  fePaperCount: number;
  pePaperCountAllStatuses: number;
  latestPaper: AdminSubjectLatestPaper | null;
  createdAt: string;
  updatedAt: string;
}
```

**Note:** `AdminSubjectLatestPaper` references `ExamPaperType` and `ExamPaperStatus`, which are declared further down the same file (`Fuexam-admin/lib/api/exam.ts:296-297`, per the existing codebase — `export type ExamPaperType = "FE" | "PE";` and `export type ExamPaperStatus = "draft" | "published";`). TypeScript type declarations in the same module are not order-sensitive, so no import reordering is needed — but if the type-check step (Step 3) reports "used before declaration", move the `AdminSubjectLatestPaper`/`AdminSubject` block to after those two type aliases instead.

Replace the existing `AdminSubjectBody` interface (lines 21-30) with:

```ts
export interface AdminSubjectBody {
  code: string;
  title: string;
  description?: string;
  coverImageUrl?: string;
  cardColor?: string;
  categorySlug?: string;
  curriculumTerm?: number | null;
  fePreviewImageCount?: number;
  active?: boolean;
  sortOrder?: number;
}
```

- [ ] **Step 2: Confirm nothing else in the file used the old shape positionally**

Run: `grep -n "AdminSubject\b" Fuexam-admin/lib/api/exam.ts`

Expected: only the type declarations and the `listExamSubjects`/`getExamSubject`/`createExamSubject`/`updateExamSubject` functions already reading/returning `AdminSubject` by name (not by field position) — no changes needed there.

- [ ] **Step 3: Type-check**

Run: `cd Fuexam-admin && npx tsc --noEmit -p tsconfig.json 2>&1 | grep -E "^lib/api/exam.ts"`

Expected: no output (no errors in this file). Errors in *other* files that consume `AdminSubject` are expected at this point and get fixed in Task 8/9 — do not fix them here.

- [ ] **Step 4: Commit**

```bash
git add Fuexam-admin/lib/api/exam.ts
git commit -m "feat(exam-admin): add curriculumTerm and latest-paper fields to AdminSubject"
```

---

### Task 8: Frontend — grouping helper + tree render in the subjects page

**Files:**
- Modify: `Fuexam-admin/app/exam/subjects/page.tsx`

**Interfaces:**
- Consumes: `AdminSubject.curriculumTerm`, `.fePaperCount`, `.pePaperCountAllStatuses`, `.feQuestionCount`, `.pePaperCount`, `.latestPaper` (Task 7); existing `filteredItems` (already computed from `search`, unchanged); existing `AdminShell`, `Card`, `Button`, `Input`, `ConfirmDialog`, `can`, `useAuth`, `Link` imports (unchanged).
- Produces: a `groupSubjects(items: AdminSubject[]): TermGroup[]` pure function (exported from the same file is not required — it is a local helper, no other file consumes it) where
  `type TermGroup = { term: number | null; label: string; subjects: AdminSubject[]; feQuestionTotal: number; paperTotal: number; latestPaper: (AdminSubjectLatestPaper & { subjectCode: string }) | null }`.

- [ ] **Step 1: Add imports**

At the top of `Fuexam-admin/app/exam/subjects/page.tsx`, add to the existing import block:

```tsx
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { ChevronDown, Folder, FileStack } from "lucide-react";
import { formatDateTime } from "@/lib/format-datetime";
```

And extend the existing `AdminSubject`/API import list to also import the new type:

```tsx
import {
  type AdminSubject,
  type AdminSubjectLatestPaper,
  createExamSubject,
  deleteExamSubject,
  listExamSubjects,
  updateExamSubject,
} from "@/lib/api/exam";
```

(This replaces the current shorter import block that only lists `AdminSubject, createExamSubject, deleteExamSubject, listExamSubjects, updateExamSubject`.)

- [ ] **Step 2: Add the grouping helper and color palette above the component function**

Insert this right after the existing `EMPTY_FORM` constant, before `export default function AdminExamSubjectsPage()`:

```tsx
type TermGroup = {
  term: number | null;
  label: string;
  subjects: AdminSubject[];
  feQuestionTotal: number;
  paperTotal: number;
  latestPaper: (AdminSubjectLatestPaper & { subjectCode: string }) | null;
};

/** Cycles through a fixed palette so each "Kỳ" badge gets a distinct, stable color. */
const TERM_BADGE_COLORS = [
  "bg-amber-500 text-amber-950",
  "bg-sky-500 text-sky-950",
  "bg-rose-500 text-rose-950",
  "bg-emerald-500 text-emerald-950",
  "bg-violet-500 text-violet-950",
  "bg-cyan-500 text-cyan-950",
  "bg-orange-500 text-orange-950",
  "bg-lime-500 text-lime-950",
  "bg-fuchsia-500 text-fuchsia-950",
  "bg-teal-500 text-teal-950",
];

function termBadgeColor(term: number): string {
  return TERM_BADGE_COLORS[term % TERM_BADGE_COLORS.length];
}

function groupSubjects(items: AdminSubject[]): TermGroup[] {
  const byTerm = new Map<number | null, AdminSubject[]>();
  for (const item of items) {
    const key = item.curriculumTerm ?? null;
    const bucket = byTerm.get(key);
    if (bucket) {
      bucket.push(item);
    } else {
      byTerm.set(key, [item]);
    }
  }

  const terms = [...byTerm.keys()].sort((a, b) => {
    if (a === null) return -1;
    if (b === null) return 1;
    return a - b;
  });

  return terms.map((term) => {
    const subjects = byTerm.get(term)!;
    const feQuestionTotal = subjects.reduce(
      (sum, s) => sum + s.feQuestionCount + s.pePaperCount,
      0,
    );
    const paperTotal = subjects.reduce(
      (sum, s) => sum + s.fePaperCount + s.pePaperCountAllStatuses,
      0,
    );
    const latestPaper = subjects.reduce<(AdminSubjectLatestPaper & { subjectCode: string }) | null>(
      (latest, s) => {
        if (!s.latestPaper) return latest;
        if (!latest || s.latestPaper.createdAt > latest.createdAt) {
          return { ...s.latestPaper, subjectCode: s.code };
        }
        return latest;
      },
      null,
    );
    return {
      term,
      label: term === null ? "Tổng hợp - Chưa rõ kỳ" : `Kỳ ${term}`,
      subjects,
      feQuestionTotal,
      paperTotal,
      latestPaper,
    };
  });
}
```

- [ ] **Step 3: Verify the helper in isolation before wiring it into JSX**

Run: `cd Fuexam-admin && npx tsc --noEmit -p tsconfig.json 2>&1 | grep -E "^app/exam/subjects/page.tsx"`

Expected: no new errors yet attributable to `groupSubjects`/`TermGroup`/`termBadgeColor` themselves (the file as a whole may still show pre-existing errors from the not-yet-updated JSX below — that's expected until Step 4).

- [ ] **Step 4: Replace the table-based render with the grouped tree**

In the same file, find the block that currently renders the subject list (the `<div className={canWrite ? "" : "lg:col-span-2"}>` wrapping the `<Table>` — currently spans from `<div className={canWrite ? "" : "lg:col-span-2"}>` through its closing `</div>`, immediately before `<ConfirmDialog`). Replace that entire `<div>...</div>` block with:

```tsx
        <div className={canWrite ? "" : "lg:col-span-2"}>
          <div className="rounded-xl border border-border">
            {loading ? (
              <p className="p-4 text-sm text-muted-foreground">Đang tải...</p>
            ) : (
              <Collapsible defaultOpen>
                <div className="flex items-center justify-between border-b border-border px-4 py-3">
                  <div>
                    <p className="text-sm font-semibold">Danh sách môn thi theo kỳ</p>
                    <p className="text-xs text-muted-foreground">
                      {items.length} môn · nhóm theo kỳ học
                    </p>
                  </div>
                  <CollapsibleTrigger asChild>
                    <Button type="button" variant="ghost" size="sm">
                      <ChevronDown className="h-4 w-4" />
                    </Button>
                  </CollapsibleTrigger>
                </div>
                <CollapsibleContent>
                  <div className="divide-y divide-border">
                    {groupSubjects(filteredItems).map((group) => (
                      <div key={group.term ?? "none"} className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-start">
                        <div className="flex shrink-0 items-center gap-2 sm:w-40">
                          {group.term === null ? (
                            <span className="flex h-8 w-8 items-center justify-center rounded-md bg-muted text-muted-foreground">
                              <FileStack className="h-4 w-4" />
                            </span>
                          ) : (
                            <span
                              className={`flex h-8 w-8 items-center justify-center rounded-full text-sm font-bold ${termBadgeColor(group.term)}`}
                            >
                              {group.term}
                            </span>
                          )}
                          <span className="text-sm font-medium">{group.label}</span>
                        </div>

                        <div className="flex flex-1 flex-wrap gap-x-3 gap-y-1.5">
                          {group.subjects.map((item) => (
                            <span key={item.id} className="inline-flex items-center gap-1">
                              <Folder className="h-3.5 w-3.5 text-muted-foreground" />
                              <Link
                                href={`/exam/subjects/${item.id}/papers`}
                                className="font-mono text-sm text-sky-500 hover:underline"
                                title={item.title}
                              >
                                {item.code}
                              </Link>
                              {canWrite && (
                                <button
                                  type="button"
                                  className="text-xs text-muted-foreground hover:text-primary"
                                  onClick={() => startEdit(item)}
                                  aria-label={`Sửa ${item.code}`}
                                >
                                  ✎
                                </button>
                              )}
                              {canDelete && (
                                <button
                                  type="button"
                                  className="text-xs text-muted-foreground hover:text-destructive"
                                  onClick={() => setDeleteId(item.id)}
                                  aria-label={`Xóa ${item.code}`}
                                >
                                  ✕
                                </button>
                              )}
                            </span>
                          ))}
                          {group.subjects.length === 0 && (
                            <span className="text-xs text-muted-foreground">Không có môn phù hợp.</span>
                          )}
                        </div>

                        <div className="flex shrink-0 gap-6 text-right sm:w-28">
                          <div>
                            <p className="text-sm font-semibold">{group.feQuestionTotal}</p>
                            <p className="text-xs text-muted-foreground">Câu hỏi</p>
                          </div>
                          <div>
                            <p className="text-sm font-semibold">{group.paperTotal}</p>
                            <p className="text-xs text-muted-foreground">Đề thi</p>
                          </div>
                        </div>

                        <div className="shrink-0 sm:w-56 sm:text-right">
                          {group.latestPaper ? (
                            <div className="flex flex-col items-start gap-1 sm:items-end">
                              <Badge variant={group.latestPaper.paperType === "FE" ? "secondary" : "outline"}>
                                {group.latestPaper.paperType === "FE" ? "Đề Thi FE" : "Đề Thi PE"}
                              </Badge>
                              <p className="max-w-[200px] truncate text-xs" title={group.latestPaper.examCode}>
                                {group.latestPaper.subjectCode} · {group.latestPaper.examCode}
                              </p>
                              <p className="text-xs text-muted-foreground">
                                {formatDateTime(group.latestPaper.createdAt)}
                              </p>
                            </div>
                          ) : (
                            <p className="text-xs text-muted-foreground">Chưa có đề nào</p>
                          )}
                        </div>
                      </div>
                    ))}
                    {filteredItems.length === 0 && (
                      <p className="px-4 py-6 text-center text-sm text-muted-foreground">
                        Không có môn thi nào.
                      </p>
                    )}
                  </div>
                </CollapsibleContent>
              </Collapsible>
            )}
          </div>
        </div>
```

This removes the `Table`/`TableBody`/`TableCell`/`TableHead`/`TableHeader`/`TableRow` usage from this file. Remove that now-unused import block (the `import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";` lines) from the top of the file — leaving it in would fail lint/type-check as an unused import in strict configs, and it is no longer referenced anywhere in this file.

- [ ] **Step 5: Type-check the whole file**

Run: `cd Fuexam-admin && npx tsc --noEmit -p tsconfig.json 2>&1 | grep -E "^app/exam/subjects/page.tsx"`

Expected: no output. If there are errors, they are almost certainly one of: (a) the removed `Table*` import still referenced somewhere you missed — search with `grep -n "Table" app/exam/subjects/page.tsx`; (b) a typo in the JSX block above — compare braces/tags carefully; fix inline, this is not a case where "similar to elsewhere" applies, the exact JSX is given above.

- [ ] **Step 6: Commit**

```bash
git add Fuexam-admin/app/exam/subjects/page.tsx
git commit -m "feat(exam-admin): render subjects grouped by curriculum term (forum-tree layout)"
```

---

### Task 9: Frontend — "Kỳ học" dropdown in the create/edit form

**Files:**
- Modify: `Fuexam-admin/app/exam/subjects/page.tsx`

**Interfaces:**
- Consumes: `Select`/`SelectTrigger`/`SelectValue`/`SelectContent`/`SelectItem` (already imported in Task 8 Step 1); `AdminSubjectBody.curriculumTerm` (Task 7).
- Produces: the create/edit form now round-trips `curriculumTerm`; no new exports.

- [ ] **Step 1: Add the sentinel constant and extend `EMPTY_FORM`**

This codebase's existing `<Select>` usage never uses an empty-string `value=""` for a "no selection" option — it always uses a non-empty sentinel instead (confirmed: `Fuexam-admin/app/exam/papers/page.tsx:45` declares `const ALL = "all";` and uses it as `<SelectItem value={ALL}>` at lines 207/219/330). Follow that exact convention here.

Right after the existing `EMPTY_FORM` constant declaration, add:

```tsx
const UNASSIGNED_TERM = "unassigned";
```

Then update the `EMPTY_FORM` constant itself to add a field:

```tsx
const EMPTY_FORM = {
  code: "",
  title: "",
  description: "",
  coverImageUrl: "",
  cardColor: "",
  categorySlug: "on-thi",
  curriculumTerm: UNASSIGNED_TERM,
  active: true,
  sortOrder: "0",
  fePreviewImageCount: "2",
};
```

(`curriculumTerm` is kept as a string in local form state, like `sortOrder`/`fePreviewImageCount` already are — `UNASSIGNED_TERM` means "chưa rõ kỳ"/unset.)

- [ ] **Step 2: Populate the field when editing**

In `startEdit(item: AdminSubject)`, add one line to the object passed to `setForm`, right after the `categorySlug` line:

```tsx
      curriculumTerm: item.curriculumTerm != null ? String(item.curriculumTerm) : UNASSIGNED_TERM,
```

- [ ] **Step 3: Include it in the submit payload**

In `handleSubmit`, add one line to the `body` object, right after the `categorySlug` line:

```tsx
      curriculumTerm:
        form.curriculumTerm === UNASSIGNED_TERM ? undefined : Number.parseInt(form.curriculumTerm, 10),
```

- [ ] **Step 4: Add the dropdown to the form JSX**

Inside the form, find the `<div className="grid grid-cols-2 gap-4">` block that currently holds "Màu thẻ (hex)" and "Danh mục (slug)" as its first two children. Add a third child immediately after the "Danh mục (slug)" field's closing `</div>` (this makes the grid go from 4 children to 5 — Tailwind's `grid-cols-2` will simply wrap it onto a new row, no layout change needed):

```tsx
                  <div className="space-y-2">
                    <Label htmlFor="curriculumTerm">Kỳ học</Label>
                    <Select
                      value={form.curriculumTerm}
                      onValueChange={(v) => setForm({ ...form, curriculumTerm: v })}
                    >
                      <SelectTrigger id="curriculumTerm">
                        <SelectValue placeholder="Chưa rõ kỳ" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={UNASSIGNED_TERM}>Chưa rõ kỳ</SelectItem>
                        {Array.from({ length: 10 }, (_, i) => i).map((term) => (
                          <SelectItem key={term} value={String(term)}>
                            Kỳ {term}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
```

- [ ] **Step 5: Type-check**

Run: `cd Fuexam-admin && npx tsc --noEmit -p tsconfig.json 2>&1 | grep -E "^app/exam/subjects/page.tsx"`

Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add Fuexam-admin/app/exam/subjects/page.tsx
git commit -m "feat(exam-admin): add Kỳ học dropdown to the subject create/edit form"
```

---

### Task 10: End-to-end verification

**Files:** none (verification only, no code changes expected — if this task finds a bug, fix it in the file it belongs to and note that deviation).

**Interfaces:** none produced; this task consumes the finished feature end to end.

- [ ] **Step 1: Full backend test suite**

Run: `cd backend && mvn -q test 2>&1 | tail -60`

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Start Postgres and the backend locally**

Run: `cd backend && docker compose up -d postgres redis`

Then: `mvn -q -pl app -am package -DskipTests 2>&1 | tail -30`

Then (background): `STORAGE_PROVIDER=local OAUTH_GOOGLE_CLIENT_ID=dummy OAUTH_GOOGLE_CLIENT_SECRET=dummy java -jar app/target/*.jar --spring.profiles.active=local > /tmp/exam-admin-ui-verify.log 2>&1 &`

Wait for `Started FuOverflowApplication` in the log before continuing (poll `grep -q "Started FuOverflowApplication" /tmp/exam-admin-ui-verify.log`).

- [ ] **Step 3: Confirm the migration applied and the new field round-trips over the real API**

Log in as an existing local admin (or create one following the same steps used in prior sessions of this project — check `docs/superpowers/plans/` for an earlier admin-bootstrap recipe if one isn't already running locally), then:

```bash
curl -s -b cookies.txt -X POST http://localhost:8080/api/v1/admin/exam/subjects \
  -H 'Content-Type: application/json' \
  -d '{"code":"UITEST101","title":"UI Test Subject","curriculumTerm":2}' | jq
```

Expected: response JSON includes `"curriculumTerm":2`, `"fePaperCount":0`, `"pePaperCountAllStatuses":0`, `"latestPaper":null`.

Then: `curl -s -b cookies.txt http://localhost:8080/api/v1/admin/exam/subjects | jq '.data[] | select(.code=="UITEST101")'`

Expected: same fields present and correct in the list response too.

Clean up the test subject afterward: `curl -s -b cookies.txt -X DELETE http://localhost:8080/api/v1/admin/exam/subjects/<id-from-above>`.

- [ ] **Step 4: Manually verify the admin UI**

Run: `cd Fuexam-admin && BACKEND_ORIGIN=http://localhost:8080 npx next dev -p 4460`

Open `http://localhost:4460/exam/subjects`, log in, and confirm:
- Subjects with no `curriculumTerm` appear under "Tổng hợp - Chưa rõ kỳ".
- Creating/editing a subject with a chosen "Kỳ học" moves it into the matching numbered group, and the group badge color is visibly distinct per kỳ.
- The "Câu hỏi"/"Đề thi" numbers per group change when a subject with existing FE questions/papers is moved between groups.
- The search box still filters subjects (chips disappear/reappear) without breaking group headers.
- Clicking a subject code still navigates to `/exam/subjects/{id}/papers` (unchanged existing page).

Report any visual defect found here as a normal bug fix in the relevant task's file, then re-run this step — do not claim the task done without having actually loaded the page.

- [ ] **Step 5: Stop local servers, clean up**

```bash
pkill -f "next dev -p 4460"
pkill -f "fuoverflow-app.*jar"
```

- [ ] **Step 6: Final commit if Step 4 required fixes**

If Step 4 required any fix, stage exactly the files touched and commit with a message describing the fix (e.g. `fix(exam-admin): correct Select empty-value handling in Kỳ dropdown`). If no fixes were needed, there is nothing to commit for this task.

---

## Plan Self-Review Notes

- **Spec coverage:** §2.1 (migration) → Task 1/2. §2.2 (API) → Tasks 3/4/5/6. §2.3 (UI grouping, chips, counts, latest activity, search reuse, collapse) → Tasks 8/9. §4 rollback/backward-compat concerns → addressed inline in Tasks 3/5 notes. §5 verification checklist → Task 10 covers every bullet (backend test, tsc, real API round-trip, manual UI check).
- **Placeholder scan:** the two items originally left as "grep and decide" hedges were resolved by reading the actual code before finalizing this plan, not left for the implementer to guess: `ExamPaperType.FE/PE.dbValue()` is confirmed at `ExamCatalogQueryService.java:182-184`; `ExamPaperEntity` has no public setters or no-arg constructor usable outside its package, so Task 6's test builds paper fixtures via the existing `ExamPaperEntity.draft(...)` factory (pattern confirmed at `ExamPaperAdminServiceTest.java:341-347`); and the `<Select>`/`SelectItem` "no selection" convention in this codebase is a non-empty sentinel string (confirmed at `Fuexam-admin/app/exam/papers/page.tsx:45,207,219,330` — `const ALL = "all"`), so Task 9 uses `UNASSIGNED_TERM = "unassigned"` throughout instead of `value=""`.
- **Type consistency:** `AdminSubjectResponse` field names (`fePaperCount`, `pePaperCountAllStatuses`, `latestPaper`) match exactly between Task 5 (DTO), Task 6 (service), and Task 7 (frontend type) — checked by re-reading each task's code block side by side after editing.
