# Remove Curriculum Term ("Kỳ 0-9"), Group by Latest Paper Term Instead — Design Spec

**Status:** Approved by user for autonomous execution (bounded reversal of a feature shipped earlier in this same session; user explicitly confirmed scope in chat before implementation).

## 1. Context

An earlier feature added `curriculum_term` (an admin-assigned integer 0-9, "which semester of the FPT curriculum a subject belongs to") to `exam_subjects`, with an admin UI dropdown to assign it and a "group subjects by Kỳ" tree in both the admin subject browser and the public client catalog. In practice this introduced a second, confusing "Kỳ" concept alongside the academic-term string (`SU26`/`FA26`/`SP26`) that already lives on each *paper* and is auto-derived from the exam code. The user found the two concepts conflicting and confirmed: drop the admin-assigned 0-9 concept entirely, and instead group subjects by **the academic term of their most recently created paper** (`SU26`/`FA26`/...), reusing data that already exists and needs no manual admin work.

The user explicitly acknowledged the tradeoff of this choice (a subject's group membership can change whenever a new paper is delivered for it) and chose it anyway over category-based or flat-list alternatives.

## 2. Decisions

### 2.1 Remove `curriculum_term` completely, don't just stop using it

New migration `V57__drop_exam_subject_curriculum_term.sql` drops the column (and its `CHECK (curriculum_term BETWEEN 0 AND 9)` constraint, which Postgres drops automatically along with the column). This feature shipped very recently in this same session with no real admin-assigned data expected to exist yet; the user confirmed a full removal, not a soft deprecation.

### 2.2 Backend: remove `curriculumTerm` everywhere it was threaded, add `term` to the existing "latest paper" summaries instead

- `ExamSubjectEntity`: remove the `curriculumTerm` field, getter, setter, and the `create(...)` factory parameter (reverts to the pre-existing 11-arg signature).
- `CreateSubjectRequest` / `UpdateSubjectRequest`: remove `curriculumTerm` (and the now-unused `@Min`/`@Max` imports if nothing else in the file needs them — check before removing).
- `AdminSubjectResponse` / `PublicSubjectCardResponse`: remove `curriculumTerm`. Add a `term` field to both nested `LatestPaperSummary` records (reading `ExamPaperEntity.getTerm()`), so the frontend can group subjects by their latest paper's academic term instead.
- `ExamSubjectAdminService` (`create`/`update`/`toAdmin`) and `ExamCatalogQueryService.toCard()`: drop every `curriculumTerm` read/write; add `paper.getTerm()` to the `LatestPaperSummary` construction in both.
- `ExamPaperIngestService.resolveOrCreateSubject(...)`: drop the `null` curriculumTerm argument it was passing to `ExamSubjectEntity.create(...)`.
- Every real call site of `ExamSubjectEntity.create(...)` (grep-verified before writing the plan: 12 real call sites across 6 files) needs the trailing-position `curriculumTerm` argument removed. These are compile-enforced — a missed site fails the build, not a silent bug — so the plan leans on `mvn compile`/`test` as the completeness gate, same approach used successfully when this argument was first added.

### 2.3 Frontend: group by the latest paper's `term`, not a subject-level field

Both `Fuexam-admin/app/exam/subjects/page.tsx` and `Fuexam/app/(app)/exam/page.tsx` change their grouping key from `item.curriculumTerm` (a field read directly off the subject) to `item.latestPaper?.term ?? null` (a field read off the nested latest-paper summary, matching what the "papers by term" admin screen already groups by). The group label is just the term string itself (`"SU26"`) or `"Chưa rõ kỳ"` for `null` — the same convention already used in the admin papers page's `groupByTerm`, for consistency. Sort: `null` group first, then ascending alphabetically (string term codes have no natural numeric order, same reasoning and convention as the admin papers page).

The per-term badge (previously a fixed-width circle showing a 0-9 digit, colored by `term % paletteLength`) becomes a pill sized to its text content, colored by a simple string hash into the same 10-color palette (keeps a stable, distinct color per term without needing a numeric term).

The admin subject form's "Kỳ học" `<Select>` dropdown, its `UNASSIGNED_TERM` sentinel, and the "cannot unassign an already-assigned term" guard are all removed — there is nothing left to assign.

### 2.4 What is explicitly out of scope

- The admin papers page's own term grouping (`Fuexam-admin/app/exam/papers/page.tsx`, added in a prior session) — it already groups by `AdminPaper.term`, which is unaffected by this change and needs no rework.
- `ExamPaperEntity.term`/`campus` and the exam-code parser — untouched, this spec only removes the subject-level concept.
- Any change to how a paper's `term` is derived or validated.

## 3. Files touched

Backend:
- Create: `backend/app/src/main/resources/db/migration/V57__drop_exam_subject_curriculum_term.sql`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamSubjectEntity.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/CreateSubjectRequest.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/UpdateSubjectRequest.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminSubjectResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectCardResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamSubjectAdminService.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java`
- Modify (compile-driven, exact list confirmed by `mvn` output): every test file constructing `ExamSubjectEntity.create(...)` or asserting `curriculumTerm`/`LatestPaperSummary` shape — `ExamSubjectAdminServiceTest.java`, `ExamCatalogQueryServiceTest.java`, `ExamPaperIngestServiceTest.java`.

Frontend:
- Modify: `Fuexam-admin/lib/api/exam.ts`
- Modify: `Fuexam-admin/app/exam/subjects/page.tsx`
- Modify: `Fuexam/lib/api/exam.ts`
- Modify: `Fuexam/app/(app)/exam/page.tsx`

## 4. Risks

- **Group membership churn** — a subject's term group changes whenever a new paper lands for it. Explicitly accepted by the user over the alternatives.
- **Destructive migration** — `DROP COLUMN` discards any admin-assigned values. Accepted; the feature is being fully removed by request, not deprecated.
- **Compile-enforced call sites** — same risk/mitigation as when this argument was added: a missed call site is a build failure, not a silent defect.
- **No headless browser** in this environment, same limitation as prior specs in this series — verified via `npm run build` + reasoning, not pixel comparison.

## 5. Verification checklist

- `mvn -pl exam -am test` green.
- `cd Fuexam-admin && npx tsc --noEmit` clean, `npm run build` succeeds.
- `cd Fuexam && npx tsc --noEmit` clean, `npm run build` succeeds.
- Manual reasoning: confirm `AdminSubjectResponse`/`PublicSubjectCardResponse` no longer expose `curriculumTerm` and both expose `latestPaper.term`; confirm the admin subject-edit form no longer has a "Kỳ học" control.
