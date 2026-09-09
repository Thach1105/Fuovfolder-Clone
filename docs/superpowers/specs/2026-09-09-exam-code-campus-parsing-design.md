# Exam Code Parsing: Campus + Order/Delimiter-Independent Term & Type — Design Spec

**Status:** Approved by user for autonomous execution (confirmed short in-chat design before implementation; user explicitly said proceed without further check-ins for this task).

## 1. Context

The EOS webhook ingest path (`EosPayloadAdapter`) derives subject code, academic term, and paper type (FE/PE) from a single packed `ExamCode` string when the sender doesn't supply them as separate fields. The current parser (`EosPayloadAdapter.EXAM_CODE`, a single regex) only recognizes one exact shape: `SUBJECT_TERM_TYPE_ID` with underscores, e.g. `SCM302_SU26_FE_553972`.

Production data now also arrives shaped `SUBJECT-TYPE-TERM-CAMPUS` with dashes, e.g. `SDN302-PE-SU26-HCM` — subject `SDN302`, type `PE`, term `SU26` (Summer 2026 — other seasons seen: `SP` = Spring, `FA` = Fall), and a training-facility/campus code `HCM` in the position the old shape used for a numeric external paper id. The user confirmed: the exact delimiter (`-` vs `_`) doesn't matter, only the field content; both the old and new shapes must keep working; campus should be persisted per paper (a subject can have papers proctored at different campuses across terms).

## 2. Decisions

### 2.1 One shared, content-based parser — not a second regex

Rather than adding a second exact-shape regex (which would only handle the two known examples and break again on the next reordering), replace the single fixed-order regex with a segment-classification parser: `ExamCodeParser` (new class, `backend/exam/src/main/java/com/fuoverflow/exam/support/ExamCodeParser.java`).

Algorithm:
1. Split the exam code on any run of `-` or `_`.
2. The **first segment is always the subject code** — this matches the existing fallback behavior for non-conforming codes (`firstSegment()` in the current adapter) and holds for both known shapes.
3. Scan every other segment (order-independent) and classify by content, not position:
   - Matches `FE|PE|PT|MID` (case-insensitive) → **type**.
   - Matches exactly 2 letters + 2 digits (case-insensitive, e.g. `SU26`, `FA26`, `SP26`) → **term**.
   - Anything else → left over.
4. If exactly one segment is left over after removing the subject/type/term matches:
   - All digits → **external paper id** (the old shape's trailing numeric id).
   - All letters → **campus** (the new shape's trailing training-facility code).
   - Otherwise (shouldn't happen given the two classifications above are mutually exhaustive for a single leftover token) → neither.
5. Two or more segments left over → the code doesn't cleanly fit either known shape; leave external id and campus both `null` rather than guessing. This reproduces today's fallback behavior for genuinely non-conforming codes (e.g. `TEST_EOS_Client_278333`, used in an existing test) exactly: 3 segments left over there, both fields stay null today and must stay null after this change.

This one parser handles both today's shape and the new one, and any other ordering of the same four concepts, without needing to special-case which shape arrived. It is verified by hand against every existing `EosPayloadAdapterTest`/`EosPayloadAdapterTest`-derived case in the implementation plan before being trusted.

### 2.2 Campus is stored per paper, nullable, no fixed vocabulary

New nullable `campus varchar(64)` column on `exam_papers` (migration `V56`), new field on `ExamPaperEntity`. No CHECK constraint enumerating valid campuses (matches the existing `term` column, which is also a free-form string with no fixed lookup table) — the set of training facilities is not this codebase's concern to validate, and a new campus code must not require a migration to become nameable.

### 2.3 Campus flows through the same paths `term` already does

- `PaperPayload` (canonical webhook envelope DTO) gains a `campus` field, so a sender that already knows its own campus can supply it directly instead of relying on `examCode` parsing — mirrors how `term` is already both derivable-from-code and explicitly-settable.
- `IngestPaper` (validated domain object) gains a `campus` field, populated from `PaperPayload.campus()`.
- `ExamPaperEntity.draft(...)` gains a `campus` parameter; `ExamPaperIngestService` passes `paper.campus()` on create and calls a new `entity.setCampus(...)` on redelivery-update (the same treatment as every other optional per-paper metadata field in that class).
- `EosPayloadAdapter` uses `ExamCodeParser` to derive `campus` (and `term`, and `type`) from the raw `ExamCode` field and populates it on the `PaperPayload` it builds.
- `ExamWebhookPayloadValidator`'s existing `EXAM_CODE_PATTERN` consistency check (canonical-envelope sanity check between explicit fields and `examCode`, only run when `examCode` matches the old strict shape) is **not** touched by this spec — it already no-ops for any code that doesn't match its exact old pattern, so a new-shape `examCode` simply skips that advisory check, which is correct: the canonical envelope's explicit fields are authoritative there regardless.

### 2.4 Admin surface

- `AdminPaperResponse`/`ExamPaperAdminService.toAdmin()` gain `campus`, additively (existing consumers of the record's other fields are unaffected).
- `Fuexam-admin/lib/api/exam.ts`'s `AdminPaper` interface gains `campus: string | null`.
- `Fuexam-admin/app/exam/papers/page.tsx` (the paper-bank review table) is restyled: papers are grouped into collapsible sections by **academic term** (`SU26`/`FA26`/`SP26`/…, with a "Chưa rõ kỳ" group for `null`, sorted alphabetically since these aren't a small fixed 0–9 range like the subject-side "Kỳ" concept), each paper row shows a small campus badge next to its exam code when `campus` is present, and a client-side campus filter (Select, populated from the distinct non-null campus values already present in the loaded page — no new backend query parameter) narrows the visible rows. Status and subject filters keep working exactly as today (server-side, unchanged). No change to `Fuexam-admin/app/exam/subjects/[id]/papers/page.tsx` (a different screen — FE question/PE item content editing, not paper-bank metadata) or to any `Fuexam/` client route (out of scope for this pass, confirmed with the user).

### 2.5 What is explicitly out of scope

- Any change to `ExamWebhookPayloadValidator.EXAM_CODE_PATTERN` or its consistency-check behavior.
- Any change to the public/anonymous exam catalog (`Fuexam/`).
- Any change to `Fuexam-admin/app/exam/subjects/[id]/papers/page.tsx`.
- A server-side campus filter query parameter — client-side filtering of the already-fetched, already-filtered-by-status/subject list is sufficient at this data scale (same reasoning already accepted for the subject-tree search box).
- Backfilling `campus` for existing rows — the column starts `NULL` for every paper ingested before this change; there is no reliable way to re-derive it retroactively for rows whose original raw `ExamCode` wasn't kept in a re-parseable place across all ingestion paths, and the spec doesn't ask for one.

## 3. Files touched

Backend:
- Create: `backend/exam/src/main/java/com/fuoverflow/exam/support/ExamCodeParser.java`
- Create: `backend/exam/src/test/java/com/fuoverflow/exam/support/ExamCodeParserTest.java`
- Create: `backend/app/src/main/resources/db/migration/V56__exam_paper_campus.sql`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/webhook/PaperPayload.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/domain/IngestPaper.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidator.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/EosPayloadAdapter.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/EosPayloadAdapterTest.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamWebhookPayloadValidatorTest.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/support/ExamPaperFingerprintTest.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperEntity.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/persistence/ExamPaperEntityTest.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperAdminServiceTest.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamSubjectAdminServiceTest.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminPaperResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperAdminService.java`

Frontend:
- Modify: `Fuexam-admin/lib/api/exam.ts`
- Modify: `Fuexam-admin/app/exam/papers/page.tsx`

## 4. Risks

- **Many call sites share the two changing record/factory signatures** (`ExamPaperEntity.draft(...)`: 6 real call sites; `PaperPayload`: 12; `IngestPaper`: 6 — all counted by grep before writing the plan). Every one is a plain Java constructor/factory call, so a missed site is a compile error, not a silent bug — the plan leans on `mvn -q -pl exam -am compile`/`test` as the authoritative completeness check rather than hand-enumerating every call, to avoid a transcription mistake being worse than the thing it guards against.
- **Segment-classification parser edge cases** — verified by hand against every existing `EosPayloadAdapterTest` case before writing the plan (documented in the plan itself), plus new cases for the dash/campus shape and delimiter/order permutations.
- **No headless browser** in this environment, same limitation as prior specs in this series — the papers-page grouping change is verified via `npm run build` + `npx tsc --noEmit` plus reasoning about the render logic, not pixel-level comparison.

## 5. Verification checklist

- `mvn -pl exam -am test` green, including new `ExamCodeParserTest` cases and updated `EosPayloadAdapterTest` cases for the dash/campus shape.
- `cd Fuexam-admin && npx tsc --noEmit` clean.
- `cd Fuexam-admin && npm run build` succeeds.
- Manual reasoning/trace: every existing `EosPayloadAdapterTest` case re-walked by hand against the new parser algorithm (done in §2.1 above and re-verified in the plan) to confirm zero regressions before any code is written.
