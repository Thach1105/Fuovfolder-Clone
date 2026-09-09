# Client Exam Catalog: Forum-Tree Subject Browser — Design Spec

**Status:** Approved for autonomous execution (user explicitly authorized deciding without further approval gates, same standing authorization as the sibling admin-side spec at `2026-09-09-exam-admin-forum-tree-ui-design.md`).

## 1. Context

The admin-side subject browser (`Fuexam-admin/app/exam/subjects/page.tsx`, branch `feat/exam-admin-forum-tree-ui`, pushed) was restyled into a forum-tree layout grouped by curriculum term ("Kỳ"), matching a reference screenshot of fuoverflow.com's public homepage "TÀI LIỆU CÁC MÔN HỌC" section. That work only touched the admin panel.

The user has now confirmed the **client-facing app** (`Fuexam/`, the actual production site the screenshot was taken from — runs on port 3336, serves `fuoverflow.com`) should get the same treatment. The client's current exam catalog page, `Fuexam/app/(app)/exam/page.tsx`, is a flat responsive card grid (2–4 columns, cover image + code + title + FE/PE paper-count chips) — it does not group by term at all today.

This spec covers restyling that one page into the same forum-tree grouped layout, reusing the `curriculum_term` column and admin-assignment workflow already shipped in `feat/exam-admin-forum-tree-ui`.

## 2. Decisions

### 2.1 Branch and dependency

This work branches from the tip of `feat/exam-admin-forum-tree-ui` (not from `main`), because it needs the `curriculum_term` column, the `ExamSubjectEntity.curriculumTerm` field, and the admin UI that assigns it — all of which exist only on that unmerged branch. **This branch cannot merge before (or without) `feat/exam-admin-forum-tree-ui` merging first**, and its own PR description must say so.

### 2.2 Backend: extend the public catalog DTO, additively

`PublicSubjectCardResponse` (`backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectCardResponse.java`) gains two new fields, appended at the end (purely additive — no field renamed or removed, so `PublicSubjectDetailResponse.related()` and every other consumer of this record stays source-compatible):

- `Integer curriculumTerm` — the same admin-assigned value already on `ExamSubjectEntity`. Read-only here; nothing in the public API can set it.
- `LatestPaperSummary latestPaper` — a small nested record `(String examCode, String paperType, Instant createdAt)`, or `null` if the subject has no published paper yet.

**Why `latestPaper` reuses the paper-level concept, not a real forum-post concept:** the reference screenshot's "BÀI VIẾT" column shows an avatar, username, and post title — that is community *forum* activity (threads/posts), not exam-paper activity. There is no existing association between an exam subject and forum threads/posts about it in this codebase — building one (which forum posts "belong" to which subject, whose avatar to show, etc.) is a separate, materially larger, cross-module feature. Ruling: substitute the same "latest activity" concept already built and shipped for admin — the most recently created **published** paper for that subject (exam code, FE/PE badge, relative time) — which is real, already-computable data that still answers "what's new for this subject." If the user wants true forum-post activity per subject later, that is a new spec, not a variation of this one.

Only `listActive()`'s underlying `toCard()` mapping needs to change — `relatedCards()` calls the same `toCard()` and will carry the same two new fields for free; `Fuexam/app/(app)/exam/[code]/page.tsx` (the subject detail page, out of scope for this spec) simply won't read the two new fields, which is harmless.

**Query approach:** for `curriculumTerm`, no new query — it's already a column on the entity being mapped. For `latestPaper`, add one repository call per subject inside `toCard()`, reusing the exact repository method added for admin: `ExamPaperRepository.findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID subjectId)` — except the public surface must only ever show a **published** paper (never a draft/pending one an anonymous visitor shouldn't see). Add a second repository method scoped to published status: `findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(UUID subjectId, String status)`, called with `ExamPaperStatus.PUBLISHED.dbValue()`. Do not reuse the admin's all-statuses method here — that would leak the existence/title of an unpublished paper to the public.

This adds one more query per subject to an already N+1-shaped method (`toCard()` already runs 2 count queries per subject); acceptable at current catalog sizes (same tradeoff already accepted and ledgered on the admin side), not to be "fixed" as part of this scope.

### 2.3 Frontend: forum-tree grouping, adapted for the public/anonymous audience

Restyle only `Fuexam/app/(app)/exam/page.tsx`. Do not touch `Fuexam/app/(app)/exam/[code]/page.tsx` or any other client route.

- Group `PublicSubjectCard[]` by `curriculumTerm` (`null` → an "Chưa phân loại" group, sorted first; then ascending 0-9), same grouping/sorting rule as the admin page's `groupSubjects`.
- Each subject renders as a folder-style link (icon + code, `title` attribute holding the full title) linking to `/exam/${item.code}` (existing route, unchanged) — no edit/delete affordances (this is the public app; there is no `canWrite`/`canDelete` concept here).
- Two badge/count elements per subject group row: an FE badge (`{sum of fePaperCount in group} đề FE`) and a PE badge (`{sum of pePaperCount in group} đề PE`), colored amber (FE) and rose (PE) — carrying forward the color intent from the original admin spec, which the admin implementation itself ended up not applying (a deviation noted but not fixed there); this client version should get it right.
- A latest-activity line per group: the most recently created `latestPaper` across the group's subjects (by `createdAt`, numeric/Date comparison — not string comparison, correcting a Minor finding recorded against the admin implementation), showing the FE/PE badge, the subject code, and a relative or short-date label (e.g. `formatDateTime` equivalent already used elsewhere in `Fuexam/lib`, or a simple `toLocaleDateString` if no shared helper exists — check `Fuexam/lib/utils` first).
- Collapsible per-group sections (reuse whatever the project already has — check `Fuexam/components/ui` for an existing `Collapsible` primitive before adding a new dependency).
- The existing search box (`Input`, filters by code/title) stays, filtering within the grouped view exactly like the admin version — filter the flat list first, then group the filtered result, and memoize the grouping (`useMemo`) since this page's data can be edited via search on every keystroke — this specifically avoids the Minor perf finding recorded against the admin implementation (ungrouped re-computation on every render).
- Loading skeleton and empty-state copy stay conceptually the same as today, adapted to the tree shape instead of the grid shape.
- Keep the existing hero copy/header section (`"Ngân hàng đề thi"` / `"Đề thi FE & PE theo mã môn"`) untouched — only the listing section below it changes shape.

### 2.4 What is explicitly NOT in scope

- The subject detail page (`[code]/page.tsx`) — untouched.
- Any forum/thread/post-to-subject association — explicitly deferred, see §2.2.
- Any change to `fePaperCount`/`pePaperCount` semantics — they already mean "published paper-bank count" on the public surface (verified in `ExamCatalogQueryService.toCard()`), unlike the admin side's historical `pePaperCount` (PE content-item count) naming collision — no rename needed here.
- Retrofixing the admin implementation's amber/rose color deviation or its string-vs-numeric date comparison — those are already ledgered Minor findings on a separate, already-pushed branch; not reopened by this spec.
- Pagination — the catalog is small enough today that loading the whole active list at once (current behavior) is unchanged.

## 3. Files touched

- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectCardResponse.java` (add 2 fields + nested record)
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java` (`toCard()`)
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperRepository.java` (add one published-only finder)
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java` (cover the two new fields, including the published-vs-draft latest-paper distinction)
- Modify: `Fuexam/lib/api/exam.ts` (`PublicSubjectCard` interface: 2 new fields + nested type)
- Modify: `Fuexam/app/(app)/exam/page.tsx` (grouping + tree render)

## 4. Risks

- **N+1 amplification** — accepted, same tradeoff already made and ledgered for admin; catalog size is small.
- **Published-vs-all-statuses leak** — the one genuinely new risk versus the admin work (which is an authenticated, permissioned surface where showing an unpublished paper's existence is fine). Mitigated by using a dedicated published-only repository method rather than reusing the admin one. A test must assert a draft/pending paper never appears as `latestPaper` on the public endpoint.
- **No headless browser available** in this environment (same limitation as the admin work) — visual/pixel confirmation against the reference screenshot is not possible; verification will again substitute real-API-data-driven logic checks plus a production `next build`.

## 5. Verification checklist

- `mvn -pl exam -am test` green, including new published-vs-draft assertion.
- `cd Fuexam && npx tsc --noEmit` clean.
- `cd Fuexam && npm run build` succeeds (existing project has no test script; build + typecheck are the meaningful gates, same substitute used for the admin frontend).
- Manual real-API round trip: an admin-assigned `curriculumTerm` (via the already-shipped admin UI) shows up correctly grouped on the public endpoint's response; a subject with only a draft paper shows no `latestPaper` publicly but does show one once published.
