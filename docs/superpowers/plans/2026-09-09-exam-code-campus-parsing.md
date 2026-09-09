# Exam Code Campus/Term Parsing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the EOS webhook's single fixed-order exam-code regex with a delimiter/order-independent, content-based parser that also extracts a training-facility "campus" code, persist campus per paper, and surface it (grouped by academic term) in the admin paper-bank review screen.

**Architecture:** A new segment-classification parser (`ExamCodeParser`) replaces the old regex inside `EosPayloadAdapter`. `campus` is threaded additively through the existing webhook DTO chain (`PaperPayload` → `IngestPaper` → `ExamPaperEntity`) the same way `term` already flows, landing in a new nullable `exam_papers.campus` column. The admin paper-bank table gains a campus badge, a client-side campus filter, and per-term collapsible grouping.

**Tech Stack:** Java 21 / Spring Boot 3.5.x (backend/exam module), Next.js App Router client component + Tailwind + shadcn/ui (Fuexam-admin).

**Spec:** `docs/superpowers/specs/2026-09-09-exam-code-campus-parsing-design.md`

## Global Constraints

- No DB foreign keys; Hibernate `ddl-auto=validate` — the new `campus` column must be a plain nullable `varchar(64)` with no CHECK constraint (matches the existing free-form `term` column).
- Migration is a new file `V56__exam_paper_campus.sql` — the highest existing migration is `V55`.
- Every constructor/factory whose signature changes here (`ExamPaperEntity.draft(...)`, `PaperPayload`, `IngestPaper`) is called from multiple places (grep-verified before writing this plan: `ExamPaperEntity.draft(...)` has 6 real call sites, `PaperPayload` has 12, `IngestPaper` has 6). These are plain Java constructor calls — a missed site is a **compile error**, not a silent bug. Treat `mvn -q -pl exam -am compile` / `mvn -q -pl exam -am test` as the authoritative completeness check for these tasks, not a manual tally.
- Do not touch `ExamWebhookPayloadValidator.EXAM_CODE_PATTERN` or `verifyExamCodeAgreement(...)` — out of scope per the spec (§2.3).
- Do not touch `Fuexam-admin/app/exam/subjects/[id]/papers/page.tsx` or anything under `Fuexam/`.
- No new backend query parameter for filtering by campus — the admin papers page filters client-side.
- Field-order rule for every signature change in this plan (keeps every insertion point unambiguous and mechanical): `PaperPayload` and `IngestPaper` get `campus` appended as the **new last field** (after `resources`); `ExamPaperEntity.draft(...)` gets `campus` inserted **immediately before the trailing `Instant now`/timestamp parameter** (verified by hand: all 6 real call sites end in a timestamp expression — `Instant.now()`, `now`, or `createdAt`).

---

### Task 1: `ExamCodeParser` — content-based, delimiter/order-independent

**Files:**
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/support/ExamCodeParser.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/support/ExamCodeParserTest.java`

**Interfaces:**
- Consumes: nothing (pure function, no dependencies).
- Produces: `ExamCodeParser.Parsed(String subjectCode, String term, String paperType, String campus, String externalPaperId)` and `ExamCodeParser.parse(String examCode)` — consumed by Task 3 (`EosPayloadAdapter`).

- [ ] **Step 1: Write the failing tests**

```java
package com.fuoverflow.exam.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExamCodeParserTest {

    @Test
    void parsesTheClassicUnderscoreShapeWithATrailingNumericId() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("SCM302_SU26_FE_553972");

        assertEquals("SCM302", p.subjectCode());
        assertEquals("SU26", p.term());
        assertEquals("FE", p.paperType());
        assertEquals("553972", p.externalPaperId());
        assertNull(p.campus());
    }

    @Test
    void parsesTheNewDashShapeWithATrailingCampusCode() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("SDN302-PE-SU26-HCM");

        assertEquals("SDN302", p.subjectCode());
        assertEquals("SU26", p.term());
        assertEquals("PE", p.paperType());
        assertEquals("HCM", p.campus());
        assertNull(p.externalPaperId());
    }

    @Test
    void isCaseInsensitiveOnTypeAndTermAndUppercasesTheResult() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("sdn302-pe-su26-hcm");

        assertEquals("SU26", p.term());
        assertEquals("PE", p.paperType());
        assertEquals("HCM", p.campus());
    }

    @Test
    void doesNotCareAboutTheOrderOfTypeAndTerm() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("SDN302_SU26_PE_HCM");

        assertEquals("SDN302", p.subjectCode());
        assertEquals("SU26", p.term());
        assertEquals("PE", p.paperType());
        assertEquals("HCM", p.campus());
    }

    @Test
    void recognizesOtherSeasonCodes() {
        assertEquals("FA26", ExamCodeParser.parse("SDN302-PE-FA26-HN").term());
        assertEquals("SP26", ExamCodeParser.parse("SDN302-PE-SP26-DN").term());
    }

    @Test
    void treatsProgressTestAndMidtermCodesAsRecognizedTypesToo() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("MAE101_SU26_PT_42");

        assertEquals("PT", p.paperType());
        assertEquals("SU26", p.term());
        assertEquals("42", p.externalPaperId());
    }

    @Test
    void leavesCampusAndExternalIdNullWhenMoreThanOneSegmentIsLeftover() {
        // Same shape the current adapter already treats as non-conforming.
        ExamCodeParser.Parsed p = ExamCodeParser.parse("TEST_EOS_Client_278333");

        assertEquals("TEST", p.subjectCode());
        assertNull(p.term());
        assertNull(p.paperType());
        assertNull(p.campus());
        assertNull(p.externalPaperId());
    }

    @Test
    void aCodeWithNoRecognizableSegmentsStillYieldsTheFirstSegmentAsSubject() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("JUSTASUBJECTCODE");

        assertEquals("JUSTASUBJECTCODE", p.subjectCode());
        assertNull(p.term());
        assertNull(p.paperType());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamCodeParserTest`
Expected: FAIL — `ExamCodeParser` does not exist yet (compile error).

- [ ] **Step 3: Implement**

```java
package com.fuoverflow.exam.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Extracts subject code, academic term, paper type, campus, and external paper id from an exam
 * code, independent of delimiter or field order.
 *
 * <p>Two shapes are seen in production: {@code SUBJECT_TERM_TYPE_ID} (underscore, trailing
 * numeric id) and {@code SUBJECT-TYPE-TERM-CAMPUS} (dash, trailing training-facility code). Both
 * — and any other ordering of the same four concepts — parse correctly here because every segment
 * after the first is classified by what it looks like, never by its position.
 */
public final class ExamCodeParser {

    private static final Pattern SEPARATOR = Pattern.compile("[-_]+");
    private static final Pattern TERM = Pattern.compile("^[A-Za-z]{2}\\d{2}$");
    private static final Pattern TYPE = Pattern.compile("^(FE|PE|PT|MID)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");
    private static final Pattern LETTERS_ONLY = Pattern.compile("^[A-Za-z]+$");

    private ExamCodeParser() {
    }

    public record Parsed(
            String subjectCode, String term, String paperType, String campus, String externalPaperId) {
    }

    /** The first segment is always the subject; every other segment is classified by content. */
    public static Parsed parse(String examCode) {
        String[] segments = SEPARATOR.split(examCode.trim());
        if (segments.length == 0) {
            return new Parsed(examCode, null, null, null, null);
        }
        String subjectCode = segments[0];

        String term = null;
        String type = null;
        List<String> leftover = new ArrayList<>();
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            if (type == null && TYPE.matcher(segment).matches()) {
                type = segment.toUpperCase();
            } else if (term == null && TERM.matcher(segment).matches()) {
                term = segment.toUpperCase();
            } else {
                leftover.add(segment);
            }
        }

        String campus = null;
        String externalPaperId = null;
        if (leftover.size() == 1) {
            String only = leftover.get(0);
            if (DIGITS_ONLY.matcher(only).matches()) {
                externalPaperId = only;
            } else if (LETTERS_ONLY.matcher(only).matches()) {
                campus = only.toUpperCase();
            }
        }

        return new Parsed(subjectCode, term, type, campus, externalPaperId);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamCodeParserTest`
Expected: PASS, all 8 tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/support/ExamCodeParser.java \
        backend/exam/src/test/java/com/fuoverflow/exam/support/ExamCodeParserTest.java
git commit -m "feat(exam): add content-based exam code parser (subject/term/type/campus)"
```

---

### Task 2: Migration — `exam_papers.campus`

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V56__exam_paper_campus.sql`

**Interfaces:**
- Produces: DB column `exam_papers.campus varchar(64) NULL`, consumed by Task 4 (`ExamPaperEntity`).

- [ ] **Step 1: Write the migration**

```sql
ALTER TABLE exam_papers
    ADD COLUMN campus varchar(64) NULL;
```

- [ ] **Step 2: Verify it applies**

Run: `cd backend && mvn -q -pl app -am compile` (compiles; migration is picked up at actual boot, verified end-to-end in Task 4's manual check, matching this plan series' established pattern of a real-boot check before considering schema work done).

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V56__exam_paper_campus.sql
git commit -m "feat(exam): add campus column to exam_papers"
```

---

### Task 3: `PaperPayload` + `IngestPaper` gain `campus`; `EosPayloadAdapter` uses the new parser

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/PaperPayload.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/domain/IngestPaper.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidator.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/EosPayloadAdapter.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/EosPayloadAdapterTest.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidatorTest.java` (11 real `new PaperPayload(...)` call sites — grep-verified)
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java` (2 real `new IngestPaper(...)` call sites)
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/support/ExamPaperFingerprintTest.java` (3 real `new IngestPaper(...)` call sites)

**Interfaces:**
- Consumes: `ExamCodeParser.parse(...)` (Task 1).
- Produces: `PaperPayload.campus()`, `IngestPaper.campus()` — consumed by Task 4 (`ExamPaperIngestService`).

- [ ] **Step 1: Add `campus` to `PaperPayload` (append as the new last field)**

```java
package com.fuoverflow.exam.api.dto.webhook;

import java.math.BigDecimal;
import java.util.List;

/**
 * One paper. {@code questions} belongs to FE deliveries; {@code images} and {@code resources}
 * belong to PE deliveries. Sending the wrong set for the declared type is rejected rather than
 * silently ignored, so a mis-shaped delivery never lands as a half-empty paper.
 */
public record PaperPayload(
        String examCode,
        String paperType,
        String subjectCode,
        String term,
        String retakeLabel,
        String title,
        String description,
        Integer durationMinutes,
        BigDecimal totalMark,
        Integer declaredQuestionCount,
        PaperSourcePayload source,
        List<QuestionPayload> questions,
        List<AssetPayload> images,
        List<ResourcePayload> resources,
        String campus
) {
}
```

- [ ] **Step 2: Add `campus` to `IngestPaper` (append as the new last field)**

```java
package com.fuoverflow.exam.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * A validated exam paper ready to be persisted. FE papers carry {@code questions} and no
 * images/resources; PE papers carry {@code images} and/or {@code resources} and no questions.
 */
public record IngestPaper(
        String examCode,
        ExamPaperType paperType,
        String subjectCode,
        String term,
        String retakeLabel,
        String title,
        String description,
        Integer durationMinutes,
        BigDecimal totalMark,
        Integer declaredQuestionCount,
        String sourceSystem,
        String externalPaperId,
        List<IngestQuestion> questions,
        List<IngestAsset> images,
        List<IngestResource> resources,
        String campus
) {
}
```

- [ ] **Step 3: Fix compilation, one file at a time, in this order**

Run `cd backend && mvn -q -pl exam -am compile` now — it will fail with an argument-count error at every one of the 12 `new PaperPayload(...)` call sites and 6 `new IngestPaper(...)` call sites (this is expected: the two record changes above are complete but nothing that calls them has been updated yet). Fix them as follows, then re-compile until clean:

1. **`ExamWebhookPayloadValidator.java`** (the one production `IngestPaper` construction) — in the `validate(...)` method, change the final `return new IngestPaper(...)` call to append `paper.campus() != null ? paper.campus().trim().toUpperCase() : null` (reuse the existing `trimmed(...)` helper already in this file, then uppercase — canonical senders may send lowercase, keep it consistent with the derived-from-code path which always uppercases) as the new last argument:
   ```java
   String campus = trimmed(paper.campus());
   ...
   return new IngestPaper(
           examCode,
           paperType,
           subjectCode,
           term,
           trimmed(paper.retakeLabel()),
           title,
           trimmed(paper.description()),
           paper.durationMinutes(),
           paper.totalMark(),
           paper.declaredQuestionCount(),
           paper.source() != null ? trimmed(paper.source().system()) : null,
           paper.source() != null ? trimmed(paper.source().externalPaperId()) : null,
           validateQuestions(questions),
           validateAssets(images),
           validateResources(resources),
           campus != null ? campus.toUpperCase() : null);
   ```
   Declare `String campus = trimmed(paper.campus());` right next to the existing `String term = trimmed(paper.term());` line, before `verifyExamCodeAgreement(...)` (campus needs no agreement check — out of scope per Global Constraints).

2. **`EosPayloadAdapter.java`** (the one production `PaperPayload` construction) — replace the whole `EXAM_CODE` `Pattern` field and the parsing block in `adapt(...)` with a call to `ExamCodeParser.parse(...)`:
   ```java
   import com.fuoverflow.exam.support.ExamCodeParser;
   import com.fuoverflow.exam.support.Sha256;
   ```
   Remove the old `EXAM_CODE` `Pattern` field entirely. Replace:
   ```java
        Matcher matcher = EXAM_CODE.matcher(examCode);
        boolean standard = matcher.matches();
        String subjectCode = standard ? matcher.group("subject") : firstSegment(examCode);
        String term = standard ? matcher.group("term") : null;
        String externalPaperId = standard ? matcher.group("id") : null;

        PaperPayload paper = new PaperPayload(
                examCode,
                // The table stores FE or PE only; a progress test is still a set of MCQs, so it
                // lands as FE rather than being refused.
                standard && "PE".equals(matcher.group("type")) ? "PE" : "FE",
                subjectCode,
                term,
                null,
                examCode,
                null,
                integer(payload.get("Duration")),
                decimal(payload.get("Mark")),
                integer(payload.get("NoOfQuestion")),
                new PaperSourcePayload("eos", externalPaperId, Instant.now()),
                questions,
                List.of(),
                List.of());
   ```
   with:
   ```java
        ExamCodeParser.Parsed parsed = ExamCodeParser.parse(examCode);

        PaperPayload paper = new PaperPayload(
                examCode,
                // The table stores FE or PE only; a progress test is still a set of MCQs, so it
                // lands as FE rather than being refused.
                "PE".equals(parsed.paperType()) ? "PE" : "FE",
                parsed.subjectCode(),
                parsed.term(),
                null,
                examCode,
                null,
                integer(payload.get("Duration")),
                decimal(payload.get("Mark")),
                integer(payload.get("NoOfQuestion")),
                new PaperSourcePayload("eos", parsed.externalPaperId(), Instant.now()),
                questions,
                List.of(),
                List.of(),
                parsed.campus());
   ```
   Delete the now-unused `firstSegment(String examCode)` private helper (its job is fully absorbed by `ExamCodeParser`) and the `java.util.regex.Matcher`/`Pattern` imports if no longer referenced elsewhere in the file (check: `Matcher`/`Pattern` are not used anywhere else in `EosPayloadAdapter.java` — confirm with `grep -n "Matcher\|Pattern" EosPayloadAdapter.java` before removing the imports).

3. **`EosPayloadAdapterTest.java`** — add these new test cases (existing ones need no change; re-verify each existing test still passes after the `ExamCodeParser` swap, per the by-hand trace already done in the spec §2.1):
   ```java
    @Test
    void parsesTheNewDashShapeAndCapturesCampus() {
        PaperWebhookRequest request = adapt("""
                {"ExamCode":"SDN302-PE-SU26-HCM",
                 "GrammarQuestions":[{"QID":1,"Text":"x","ImageData":"%s"}]}
                """.formatted(PNG));

        assertEquals("SDN302", request.paper().subjectCode());
        assertEquals("PE", request.paper().paperType());
        assertEquals("SU26", request.paper().term());
        assertEquals("HCM", request.paper().campus());
    }

    @Test
    void oldUnderscoreShapeStillHasNoCampus() {
        PaperWebhookRequest request = adapt(onePaper("SCM302_SU26_FE_553972"));

        assertNull(request.paper().campus());
    }
   ```
   Add `import static org.junit.jupiter.api.Assertions.assertNull;` to the existing static imports (alphabetical position) if not already present in this file (check first).

4. **`ExamWebhookPayloadValidatorTest.java`** — grep confirms exactly 11 real `new PaperPayload(...)` calls in this file. Every one must gain a trailing `null` argument (this test file never exercises an explicit `campus` on the canonical envelope — that is proven separately by a new test below) — append `, null` as the new last argument to close each of the 11 calls. Then add one new test proving the canonical envelope's own explicit `campus` field passes through untouched:
   ```java
    @Test
    void passesAnExplicitCampusThroughUnchanged() {
        PaperWebhookRequest request = new PaperWebhookRequest("evt-1", "exam.paper.upserted", Instant.now(),
                new PaperPayload("PRJ301_SU26_PE_1", "PE", "PRJ301", "SU26", null, "PE 1", "desc",
                        null, null, null, source(), List.of(),
                        List.of(new AssetPayload(0, "image/png", (long) PNG.length, null, PNG_BASE64)),
                        List.of(), "hcm"));

        IngestPaper paper = validator.validate(request);

        assertEquals("HCM", paper.campus());
    }
   ```
   (This test proves lowercase `"hcm"` from a canonical sender is normalized to `"HCM"` by the validator's `campus.toUpperCase()` from Step 3.1 above.) Place it near the other `PaperPayload`-focused tests; add `Instant` to imports if not already present (check first — likely already imported given `PaperSourcePayload` uses `Instant`).

5. **`ExamPaperIngestServiceTest.java`** — grep confirms exactly 2 real `new IngestPaper(...)` calls. Append `, null` as the new last argument to each.

6. **`ExamPaperFingerprintTest.java`** — grep confirms exactly 3 real `new IngestPaper(...)` calls. Append `, null` as the new last argument to each (campus is not part of the fingerprint per `ExamPaperFingerprint.of(...)`, which only reads `examCode`/`paperType`/content — confirm this file's tests don't need any assertion changes, only the constructor arity fix).

- [ ] **Step 4: Run the full exam module test suite**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS. If any `new PaperPayload(...)` or `new IngestPaper(...)` call site was missed, this fails with a compile error naming the exact file and line — fix it and re-run. Do not consider this task done until this command exits clean.

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/PaperPayload.java \
        backend/exam/src/main/java/com/fuoverflow/exam/domain/IngestPaper.java \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidator.java \
        backend/exam/src/main/java/com/fuoverflow/exam/application/EosPayloadAdapter.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/EosPayloadAdapterTest.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidatorTest.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java \
        backend/exam/src/test/java/com/fuoverflow/exam/support/ExamPaperFingerprintTest.java
git commit -m "feat(exam): thread campus through the webhook payload chain, parse it via ExamCodeParser"
```

---

### Task 4: `ExamPaperEntity` + `ExamPaperIngestService` — persist and wire `campus`

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperEntity.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/persistence/ExamPaperEntityTest.java` (1 real call site)
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperAdminServiceTest.java` (1 real call site)
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamSubjectAdminServiceTest.java` (1 real call site)
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java` (1 real call site)
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java` (1 real `ExamPaperEntity.draft(...)` call site — separate from the `IngestPaper` ones fixed in Task 3)

**Interfaces:**
- Consumes: `IngestPaper.campus()` (Task 3).
- Produces: `ExamPaperEntity.getCampus()`, consumed by Task 5 (`AdminPaperResponse`).

- [ ] **Step 1: Add the field, getter, setter, and extend `draft(...)`**

In `ExamPaperEntity.java`, add the column field right after `retakeLabel`:
```java
    @Column(name = "retake_label", length = 64)
    private String retakeLabel;

    @Column(name = "campus", length = 64)
    private String campus;

```
Add the getter next to `getRetakeLabel()`:
```java
    public String getRetakeLabel() { return retakeLabel; }
    public String getCampus() { return campus; }
```
Add the setter next to `setRetakeLabel(...)`:
```java
    public void setRetakeLabel(String retakeLabel) { this.retakeLabel = retakeLabel; }
    public void setCampus(String campus) { this.campus = campus; }
```
Change the `draft(...)` factory signature to insert `String campus` immediately before the trailing `Instant now`, and set the field in the body immediately before `e.createdAt = now;`:
```java
    public static ExamPaperEntity draft(
            UUID id, UUID subjectId, ExamPaperType paperType, String examCode, String term,
            String retakeLabel, String title, String description, Integer durationMinutes,
            BigDecimal totalMark, Integer declaredQuestionCount, String fingerprint,
            String ingestSource, String externalPaperId, int sortOrder, String campus, Instant now) {
        ExamPaperEntity e = new ExamPaperEntity();
        e.id = id;
        e.subjectId = subjectId;
        e.paperType = paperType.dbValue();
        e.examCode = examCode;
        e.term = term;
        e.retakeLabel = retakeLabel;
        e.title = title;
        e.description = description;
        e.durationMinutes = durationMinutes;
        e.totalMark = totalMark;
        e.declaredQuestionCount = declaredQuestionCount;
        e.fingerprint = fingerprint;
        e.status = ExamPaperStatus.DRAFT.dbValue();
        e.ingestSource = ingestSource;
        e.externalPaperId = externalPaperId;
        e.sortOrder = sortOrder;
        e.campus = campus;
        e.viewCount = 0;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
```

- [ ] **Step 2: Wire `ExamPaperIngestService`**

In `ingest(...)`, the `ExamPaperEntity.draft(...)` call currently ends:
```java
                (int) paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(
                        subject.getId(), paper.paperType().dbValue()),
                now));
```
Change to insert `paper.campus(),` immediately before `now));`:
```java
                (int) paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(
                        subject.getId(), paper.paperType().dbValue()),
                paper.campus(),
                now));
```
In `applyMetadata(...)` (the redelivery/update path), add `entity.setCampus(paper.campus());` alongside the other `entity.setXxx(paper.xxx())` calls:
```java
    private void applyMetadata(ExamPaperEntity entity, IngestPaper paper,
                               String fingerprint, String ingestSource, Instant now) {
        entity.setTitle(paper.title());
        entity.setTerm(paper.term());
        entity.setRetakeLabel(paper.retakeLabel());
        entity.setCampus(paper.campus());
        entity.setDescription(paper.description());
        entity.setDurationMinutes(paper.durationMinutes());
        entity.setTotalMark(paper.totalMark());
        entity.setDeclaredQuestionCount(paper.declaredQuestionCount());
        entity.setExternalPaperId(paper.externalPaperId());
        entity.setIngestSource(ingestSource);
        entity.setFingerprint(fingerprint);
        entity.setUpdatedAt(now);
    }
```

- [ ] **Step 3: Fix every other real `ExamPaperEntity.draft(...)` call site**

Run `cd backend && mvn -q -pl exam -am compile` — it now fails at the 5 remaining real call sites (`ExamPaperEntityTest.java`, `ExamPaperAdminServiceTest.java`, `ExamSubjectAdminServiceTest.java`, `ExamCatalogQueryServiceTest.java`, `ExamPaperIngestServiceTest.java`'s own `draft(...)` fixture — separate from the `IngestPaper` calls already fixed in Task 3). Each one ends in a timestamp expression (`Instant.now()`, `now`, or `createdAt` — verified by hand for every one of these files before this plan was written); insert `null` immediately before that trailing timestamp argument in each. For example, in `ExamCatalogQueryServiceTest.java`'s `draftFePaper()` helper, change:
```java
    private ExamPaperEntity draftFePaper() {
        return ExamPaperEntity.draft(
                UUID.randomUUID(), subjectId, ExamPaperType.FE, "MLN111_SU26_FE_1",
                "SU26", null, "MLN111 FE", null, 60, null, 50,
                "a".repeat(64), "webhook:eos-crawler", "1", 0, Instant.now());
    }
```
to:
```java
    private ExamPaperEntity draftFePaper() {
        return ExamPaperEntity.draft(
                UUID.randomUUID(), subjectId, ExamPaperType.FE, "MLN111_SU26_FE_1",
                "SU26", null, "MLN111 FE", null, 60, null, 50,
                "a".repeat(64), "webhook:eos-crawler", "1", 0, null, Instant.now());
    }
```
Apply the same "insert `null` right before the final timestamp argument" transformation to the other 4 call sites. Add one new assertion proving campus round-trips, in `ExamPaperIngestServiceTest.java` (find the existing test that exercises a full `ingest(...)` create path and add an assertion on the resulting entity's/response's campus — adapt to whatever that test already asserts on term/examCode, following the same pattern):
```java
        assertEquals("HCM", saved.getCampus());
```
(substitute a paper whose `IngestPaper` fixture in that test now carries a non-null campus — pick one existing `IngestPaper`-building helper in this file, per Task 3, and pass `"HCM"` instead of `null` for its new trailing `campus` argument specifically in the test case you're adding this assertion to, leaving other tests' fixtures at `null` as Task 3 left them).

- [ ] **Step 4: Run the full exam module test suite**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS, zero compile errors, campus round-trip assertion green.

- [ ] **Step 5: Boot the app and verify the migration + entity mapping agree**

Run the app locally with `--spring.profiles.active=local` against a real Postgres (same procedure used earlier in this series — `docker compose up -d postgres`, then `mvn -pl app -am spring-boot:run -Dspring-boot.run.profiles=local`, or the packaged-jar approach used previously). Confirm no Hibernate schema-validation error for `exam_papers.campus` (a `String` field maps to `varchar`, and the migration declares `varchar(64)` — same type family, no `smallint`-style trap expected here, but boot it for real anyway, per this series' established practice of never trusting a type mapping without an actual boot). Stop the app afterward.

- [ ] **Step 6: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperEntity.java \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/persistence/ExamPaperEntityTest.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperAdminServiceTest.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamSubjectAdminServiceTest.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java
git commit -m "feat(exam): persist campus on exam papers and wire it through ingest"
```

---

### Task 5: Admin backend surface — `AdminPaperResponse.campus`

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminPaperResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperAdminService.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperAdminServiceTest.java`

**Interfaces:**
- Consumes: `ExamPaperEntity.getCampus()` (Task 4).
- Produces: `AdminPaperResponse.campus()`, consumed by Task 6 (frontend `AdminPaper`).

- [ ] **Step 1: Write the failing test**

Add to `ExamPaperAdminServiceTest.java` (find the existing test that calls `service.list(...)` or `service.get(...)` on a paper built via this file's local `draft(ExamPaperType type)` helper — extend that helper, or add a focused new test; follow whichever existing pattern this file already uses for asserting a single field end-to-end):
```java
    @Test
    void listIncludesCampus() {
        ExamPaperEntity paper = draft(ExamPaperType.FE);
        paper.setCampus("HCM");
        when(paperRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of(paper));

        List<AdminPaperResponse> result = service.list(null, null);

        assertEquals("HCM", result.get(0).campus());
    }
```
(Adjust the mock setup line to match whichever repository method this test file already stubs for its no-filter `list(...)` tests — check the existing tests in this file for the established stubbing pattern before adding this one, since `list(null, null)` resolves to `paperRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()` per `ExamPaperAdminService.list(...)`'s current branching.)

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamPaperAdminServiceTest`
Expected: FAIL — `AdminPaperResponse` has no `campus()` accessor yet.

- [ ] **Step 3: Extend the DTO and the mapping**

```java
package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminPaperResponse(
        UUID id,
        UUID subjectId,
        String paperType,
        String examCode,
        String term,
        String retakeLabel,
        String title,
        String status,
        String ingestSource,
        int questionCount,
        int resourceCount,
        Instant publishedAt,
        Instant createdAt,
        String campus
) {
}
```

In `ExamPaperAdminService.java`'s `toAdmin(...)`, append `paper.getCampus()` as the new last constructor argument:
```java
        return new AdminPaperResponse(
                paper.getId(),
                paper.getSubjectId(),
                paper.getPaperType(),
                paper.getExamCode(),
                paper.getTerm(),
                paper.getRetakeLabel(),
                paper.getTitle(),
                paper.getStatus(),
                paper.getIngestSource(),
                questionCount,
                resourceCount,
                paper.getPublishedAt(),
                paper.getCreatedAt(),
                paper.getCampus());
```

- [ ] **Step 4: Run the full exam module test suite**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminPaperResponse.java \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperAdminService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperAdminServiceTest.java
git commit -m "feat(exam): expose campus on the admin paper response"
```

---

### Task 6: Frontend types — `AdminPaper.campus`

**Files:**
- Modify: `Fuexam-admin/lib/api/exam.ts`

**Interfaces:**
- Consumes: nothing new.
- Produces: `AdminPaper.campus: string | null` — consumed by Task 7.

- [ ] **Step 1: Extend the interface**

```ts
export interface AdminPaper {
  id: string;
  subjectId: string;
  paperType: ExamPaperType;
  examCode: string;
  term: string | null;
  retakeLabel: string | null;
  title: string;
  status: ExamPaperStatus;
  ingestSource: string | null;
  questionCount: number;
  resourceCount: number;
  publishedAt: string | null;
  createdAt: string;
  campus: string | null;
}
```

- [ ] **Step 2: Typecheck**

Run: `cd Fuexam-admin && npx tsc --noEmit`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add Fuexam-admin/lib/api/exam.ts
git commit -m "feat(exam-admin): add campus to AdminPaper"
```

---

### Task 7: Admin papers page — group by term, show campus, filter by campus

**Files:**
- Modify: `Fuexam-admin/app/exam/papers/page.tsx`

**Interfaces:**
- Consumes: `AdminPaper.campus`/`.term` (Task 6).
- Produces: nothing consumed by a later task — this is the last task.

- [ ] **Step 1: Add grouping state and a campus filter, keep the existing status/subject filters and the webhook-events tab untouched**

Add near the top of the file, after the existing `ALL`/`STATUS_LABELS`/`EVENT_STATUS_LABELS` constants:
```ts
type TermGroup = { term: string | null; label: string; papers: AdminPaper[] };

function groupByTerm(papers: AdminPaper[]): TermGroup[] {
  const byTerm = new Map<string | null, AdminPaper[]>();
  for (const paper of papers) {
    const key = paper.term ?? null;
    const bucket = byTerm.get(key);
    if (bucket) {
      bucket.push(paper);
    } else {
      byTerm.set(key, [paper]);
    }
  }

  const terms = [...byTerm.keys()].sort((a, b) => {
    if (a === null) return -1;
    if (b === null) return 1;
    return a.localeCompare(b);
  });

  return terms.map((term) => ({
    term,
    label: term === null ? "Chưa rõ kỳ" : term,
    papers: byTerm.get(term)!,
  }));
}
```
Add `Collapsible`/`CollapsibleContent`/`CollapsibleTrigger` and `ChevronDown` to the imports (already used by the sibling `subjects/page.tsx` — same components, same import paths):
```ts
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { ChevronDown } from "lucide-react";
```
Add a `campusFilter` state next to the existing `subjectFilter`/`statusFilter`:
```ts
  const [campusFilter, setCampusFilter] = useState<string>(ALL);
```
Add a `useMemo` deriving the distinct campus values present in the currently-loaded `papers`, and a memoized filtered+grouped view, right after the existing `draftCount` memo:
```ts
  const campuses = useMemo(() => {
    const set = new Set<string>();
    papers.forEach((p) => {
      if (p.campus) set.add(p.campus);
    });
    return [...set].sort();
  }, [papers]);

  const filteredPapers = useMemo(() => {
    if (campusFilter === ALL) return papers;
    return papers.filter((p) => p.campus === campusFilter);
  }, [papers, campusFilter]);

  const groups = useMemo(() => groupByTerm(filteredPapers), [filteredPapers]);
```
Add the campus `<Select>` next to the existing subject filter (inside the same `<div className="mb-4 flex flex-wrap items-center gap-3">` block, right after the subject `<Select>`'s closing `</div>`):
```tsx
            <div className="w-40">
              <Select value={campusFilter} onValueChange={setCampusFilter}>
                <SelectTrigger>
                  <SelectValue placeholder="Cơ sở" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>Tất cả cơ sở</SelectItem>
                  {campuses.map((c) => (
                    <SelectItem key={c} value={c}>
                      {c}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
```

- [ ] **Step 2: Replace the flat `<Table>` body with per-term collapsible groups**

Replace the whole `<Table>...</Table>` block inside `<TabsContent value="papers" className="mt-4">` (everything from `<Table>` through its matching `</Table>`, i.e. the papers list table — not the webhooks tab's table, which is untouched) with:
```tsx
          <div className="rounded-xl border border-border">
            {loading ? (
              <p className="p-4 text-sm text-muted-foreground">Đang tải…</p>
            ) : filteredPapers.length === 0 ? (
              <p className="p-4 text-center text-sm text-muted-foreground">
                Chưa có đề nào khớp bộ lọc.
              </p>
            ) : (
              <Collapsible defaultOpen>
                <div className="flex items-center justify-between border-b border-border px-4 py-3">
                  <div>
                    <p className="text-sm font-semibold">Đề thi theo kỳ</p>
                    <p className="text-xs text-muted-foreground">
                      {filteredPapers.length} đề · nhóm theo kỳ học
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
                    {groups.map((group) => (
                      <Collapsible key={group.term ?? "none"} defaultOpen>
                        <div className="flex items-center justify-between px-4 py-2">
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-semibold">{group.label}</span>
                            <span className="text-xs text-muted-foreground">
                              {group.papers.length} đề
                            </span>
                          </div>
                          <CollapsibleTrigger asChild>
                            <Button type="button" variant="ghost" size="sm">
                              <ChevronDown className="h-4 w-4" />
                            </Button>
                          </CollapsibleTrigger>
                        </div>
                        <CollapsibleContent>
                          <Table>
                            <TableHeader>
                              <TableRow>
                                <TableHead>Mã đề</TableHead>
                                <TableHead>Môn</TableHead>
                                <TableHead>Loại</TableHead>
                                <TableHead>Cơ sở</TableHead>
                                <TableHead className="text-right">Câu / Ảnh</TableHead>
                                <TableHead className="text-right">File</TableHead>
                                <TableHead>Nguồn</TableHead>
                                <TableHead>Trạng thái</TableHead>
                                <TableHead>Nhận lúc</TableHead>
                                <TableHead className="text-right">Hành động</TableHead>
                              </TableRow>
                            </TableHeader>
                            <TableBody>
                              {group.papers.map((paper) => (
                                <TableRow key={paper.id}>
                                  <TableCell className="font-mono text-xs">{paper.examCode}</TableCell>
                                  <TableCell>{subjectCode.get(paper.subjectId) ?? "—"}</TableCell>
                                  <TableCell>
                                    <Badge variant={paper.paperType === "FE" ? "secondary" : "outline"}>
                                      {paper.paperType}
                                    </Badge>
                                  </TableCell>
                                  <TableCell>
                                    {paper.campus ? (
                                      <span className="rounded bg-muted px-1.5 py-0.5 text-xs font-medium">
                                        {paper.campus}
                                      </span>
                                    ) : (
                                      "—"
                                    )}
                                  </TableCell>
                                  <TableCell className="text-right">{paper.questionCount}</TableCell>
                                  <TableCell className="text-right">{paper.resourceCount}</TableCell>
                                  <TableCell className="text-xs text-muted-foreground">
                                    {paper.ingestSource ?? "—"}
                                  </TableCell>
                                  <TableCell>
                                    <Badge variant={paper.status === "draft" ? "outline" : "default"}>
                                      {STATUS_LABELS[paper.status]}
                                    </Badge>
                                  </TableCell>
                                  <TableCell className="text-xs text-muted-foreground">
                                    {formatDateTime(paper.createdAt)}
                                  </TableCell>
                                  <TableCell className="space-x-2 text-right">
                                    <Button size="sm" variant="outline" onClick={() => openContent(paper)}>
                                      Xem
                                    </Button>
                                    {canPublish && paper.status === "draft" && (
                                      <Button
                                        size="sm"
                                        onClick={() => onPublish(paper)}
                                        disabled={busyId === paper.id}
                                      >
                                        Phát hành
                                      </Button>
                                    )}
                                    {canDelete && (
                                      <Button
                                        size="sm"
                                        variant="destructive"
                                        onClick={() => setDeleteTarget(paper)}
                                        disabled={busyId === paper.id}
                                      >
                                        Xóa
                                      </Button>
                                    )}
                                  </TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </CollapsibleContent>
                      </Collapsible>
                    ))}
                  </div>
                </CollapsibleContent>
              </Collapsible>
            )}
          </div>
```
Note `error && <p ...>` stays where it already is, right above this block, untouched.

- [ ] **Step 2b: Self-check before moving on**

Re-read the edited file's imports: `Table`/`TableBody`/`TableCell`/`TableHead`/`TableHeader`/`TableRow` are still used (by the per-group table above and by the untouched webhooks tab), so their imports stay. Confirm no unused imports were introduced or left behind (`Collapsible*` and `ChevronDown` are now used; everything else is unchanged).

- [ ] **Step 3: Typecheck and build**

Run: `cd Fuexam-admin && npx tsc --noEmit`
Expected: clean.

Run: `cd Fuexam-admin && npm run build`
Expected: succeeds, `/exam/papers` still listed as a route.

- [ ] **Step 4: Commit**

```bash
git add Fuexam-admin/app/exam/papers/page.tsx
git commit -m "feat(exam-admin): group papers by term and show/filter by campus"
```

---

## Plan Self-Review Notes

- Spec coverage: §2.1 (parser) → Task 1; §2.2 (schema) → Task 2; §2.3 (webhook chain wiring) → Tasks 3-4; §2.4 (admin surface) → Tasks 5-7; §2.5 (out of scope) → nothing in this plan touches those files/params, confirmed by the file lists above.
- Placeholder scan: none — every step has literal code or an exact command; the few "apply the same transformation to the remaining N call sites" instructions (Tasks 3 and 4) are backed by grep-verified exact counts and a compile-driven completeness gate, not vague guidance.
- Type/name consistency verified across tasks: `ExamCodeParser.Parsed.campus/term/paperType/externalPaperId` (Task 1) → `PaperPayload.campus` populated from `parsed.campus()` (Task 3) → `IngestPaper.campus` (Task 3) → `ExamPaperEntity.campus`/`draft(...)`'s new parameter (Task 4) → `AdminPaperResponse.campus` (Task 5) → `AdminPaper.campus` (Task 6) → `paper.campus` read in the render (Task 7) — same name throughout, no renames.
- Every real call site of the three changing signatures was grep-counted before writing this plan (`ExamPaperEntity.draft(`: 6, `new PaperPayload(`: 12, `new IngestPaper(`: 6) and each file's exact count is stated in its owning task, so an implementer can self-verify against `mvn compile`/`test` rather than trusting a hand tally.
- The by-hand trace of every existing `EosPayloadAdapterTest` case against the new `ExamCodeParser` algorithm (done while writing the spec) confirmed zero regressions before any code was written — re-stated in Task 3's context for the implementer.
