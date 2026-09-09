# Client Exam Catalog Forum-Tree Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the client-facing exam catalog page (`Fuexam/app/(app)/exam/page.tsx`) into the same forum-tree layout (grouped by curriculum term "Kỳ") already shipped for the admin panel, backed by two new read-only fields on the public subject-card API.

**Architecture:** Extend the existing public `PublicSubjectCardResponse` additively with `curriculumTerm` and a `latestPaper` summary (published papers only), compute the latter via one new published-scoped repository finder, then port the admin page's grouping/rendering logic to the client page with public-audience adjustments (no edit/delete affordances, links to `/exam/[code]` instead of the admin's papers route).

**Tech Stack:** Java 21 / Spring Boot 3.5.x (backend/exam module), Next.js App Router client component + Tailwind + shadcn/ui (Fuexam).

**Spec:** `docs/superpowers/specs/2026-09-09-exam-client-forum-tree-ui-design.md`

## Global Constraints

- This branch (`feat/exam-client-forum-tree-ui`) is based on `feat/exam-admin-forum-tree-ui`'s tip, not `main` — it depends on that branch's `curriculum_term` column/entity field, which already exist here. Do not re-add the migration or entity field.
- No new database migration in this plan — no schema change is needed.
- No DB foreign keys; Hibernate `ddl-auto=validate` stays as-is (unaffected — no schema change).
- Every DTO change is purely additive — no existing field on `PublicSubjectCardResponse` is renamed, removed, or reordered ahead of the new fields; both existing callers of `toCard()` (`listActive()` and `relatedCards()`) keep working unchanged.
- The public `latestPaper` must only ever reflect a **published** paper — never draft/pending. Use a new published-scoped repository method; never reuse the admin's all-statuses one on this endpoint.
- Do not touch `Fuexam/app/(app)/exam/[code]/page.tsx` or any other client route.
- Do not touch anything under `Fuexam-admin/` in this plan — that work is already complete and pushed on a separate branch.
- Frontend grouping must be memoized (`useMemo`) to avoid recomputing on every keystroke — this was a Minor finding on the admin implementation; this plan does not repeat it.
- Latest-paper date comparison for picking "the most recent across a group" must compare `Date` values (e.g. `new Date(x).getTime()`), not raw ISO strings — this was a Minor finding on the admin implementation; this plan does not repeat it.
- FE badge = amber, PE badge = rose (the spec's original color intent, not applied on the admin side — this plan applies it correctly).

---

### Task 1: Published-only "latest paper" repository finder

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperRepository.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/persistence/ExamPaperRepositoryTest.java` (does not exist yet — this repository has no dedicated test file; this method is exercised indirectly through Task 3's service-level test instead, which is the existing convention for this repository's other finders. No new test file needed.)

**Interfaces:**
- Produces: `Optional<ExamPaperEntity> findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(UUID subjectId, String status)` — consumed by Task 3.

- [ ] **Step 1: Add the method**

Add this method to the interface, right after the existing `findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc`:

```java
    Optional<ExamPaperEntity> findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID subjectId, String status);
```

The full file after this change:

```java
package com.fuoverflow.exam.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamPaperRepository extends JpaRepository<ExamPaperEntity, UUID> {

    Optional<ExamPaperEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ExamPaperEntity> findByFingerprintAndDeletedAtIsNull(String fingerprint);

    Optional<ExamPaperEntity> findByExamCodeIgnoreCaseAndDeletedAtIsNull(String examCode);

    List<ExamPaperEntity> findBySubjectIdAndStatusAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(
            UUID subjectId, String status);

    List<ExamPaperEntity> findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(UUID subjectId);

    List<ExamPaperEntity> findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(String status);

    List<ExamPaperEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();

    long countBySubjectIdAndPaperTypeAndStatusAndDeletedAtIsNull(
            UUID subjectId, String paperType, String status);

    long countBySubjectIdAndPaperTypeAndDeletedAtIsNull(UUID subjectId, String paperType);

    Optional<ExamPaperEntity> findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID subjectId);

    Optional<ExamPaperEntity> findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID subjectId, String status);
}
```

- [ ] **Step 2: Compile**

Run: `cd backend && mvn -q -pl exam -am compile`
Expected: BUILD SUCCESS (this is a pure interface addition; Spring Data derives the query from the method name, no implementation body needed, no test to run yet — Task 3 exercises it).

- [ ] **Step 3: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamPaperRepository.java
git commit -m "feat(exam): add published-only latest-paper finder"
```

---

### Task 2: Extend the public subject-card DTO

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectCardResponse.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `PublicSubjectCardResponse` now has 2 extra fields (`curriculumTerm`, `latestPaper`) and a nested `LatestPaperSummary(String examCode, String paperType, java.time.Instant createdAt)` record. Consumed by Task 3 (construction) and Task 5 indirectly via the frontend type (Task 4 mirrors this shape in TypeScript).

- [ ] **Step 1: Rewrite the record**

Replace the full contents of `PublicSubjectCardResponse.java` with:

```java
package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PublicSubjectCardResponse(
        UUID id,
        String code,
        String title,
        String categorySlug,
        String cardColor,
        String coverImageUrl,
        long viewCount,
        int fePaperCount,
        int pePaperCount,
        Integer curriculumTerm,
        LatestPaperSummary latestPaper
) {
    public record LatestPaperSummary(String examCode, String paperType, Instant createdAt) {
    }
}
```

- [ ] **Step 2: Compile**

Run: `cd backend && mvn -q -pl exam -am compile`
Expected: BUILD FAILURE — `ExamCatalogQueryService.java`'s single call to `new PublicSubjectCardResponse(...)` now has too few arguments for the new 11-arg constructor. This is expected; Task 3 fixes the only caller.

- [ ] **Step 3: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectCardResponse.java
git commit -m "feat(exam): add curriculumTerm and latestPaper to the public subject card"
```

(Committing a state that doesn't compile module-wide is intentional here and mirrors the sibling admin plan's Task 5 — Task 3 immediately follows and fixes the one caller; the module is never left broken across more than one task boundary.)

---

### Task 3: Populate the new fields in `ExamCatalogQueryService.toCard()`

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java`
- Test: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java`

**Interfaces:**
- Consumes: `PublicSubjectCardResponse` (Task 2), `ExamPaperRepository.findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc` (Task 1).
- Produces: nothing new for later tasks — this is the last backend task.

- [ ] **Step 1: Write the failing tests**

Add these two tests to `ExamCatalogQueryServiceTest.java`, placed right after the `subject(int previewImageCount)` helper method (i.e. as new `@Test` methods before the closing brace of the class — insert them right before the `private List<ExamFeQuestionEntity> questionsWithImages(...)` helper):

```java
    @Test
    void listActive_includesCurriculumTermAndLatestPublishedPaper() {
        ExamSubjectEntity subject = ExamSubjectEntity.create(
                subjectId, "MLN111", "Title", null, null, null, null, 3,
                2, true, 0, Instant.now());
        when(subjectRepository.findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc())
                .thenReturn(List.of(subject));
        ExamPaperEntity published = publishedFePaper();
        when(paperRepository.findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                subjectId, "published")).thenReturn(Optional.of(published));

        List<PublicSubjectCardResponse> cards = service.listActive();

        assertEquals(1, cards.size());
        PublicSubjectCardResponse card = cards.get(0);
        assertEquals(3, card.curriculumTerm());
        assertEquals("FE", card.latestPaper().paperType());
        assertEquals("MLN111_SU26_FE_1", card.latestPaper().examCode());
    }

    @Test
    void listActive_hasNoLatestPaper_whenOnlyADraftExists() {
        ExamSubjectEntity subject = ExamSubjectEntity.create(
                subjectId, "MLN111", "Title", null, null, null, null, null,
                2, true, 0, Instant.now());
        when(subjectRepository.findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc())
                .thenReturn(List.of(subject));
        when(paperRepository.findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                subjectId, "published")).thenReturn(Optional.empty());

        List<PublicSubjectCardResponse> cards = service.listActive();

        assertEquals(1, cards.size());
        assertNull(cards.get(0).curriculumTerm());
        assertNull(cards.get(0).latestPaper());
    }
```

Add `PublicSubjectCardResponse` to the existing DTO imports and `assertNull` to the existing static imports:

```java
import com.fuoverflow.exam.api.dto.PublicSubjectCardResponse;
```

```java
import static org.junit.jupiter.api.Assertions.assertNull;
```

(Both go alongside the file's existing import blocks — `PublicSubjectCardResponse` next to the other `com.fuoverflow.exam.api.dto.*` imports in alphabetical position, `assertNull` next to the other `org.junit.jupiter.api.Assertions.*` static imports in alphabetical position.)

- [ ] **Step 2: Run the new tests to verify they fail**

Run: `cd backend && mvn -q -pl exam -am test -Dtest=ExamCatalogQueryServiceTest`
Expected: FAIL — `card.curriculumTerm()` / `card.latestPaper()` don't exist as populated values yet (`toCard()` doesn't call the new constructor args), or a compile error if `toCard()` doesn't pass enough arguments (it won't, since Task 2 only changed the DTO, not this service).

- [ ] **Step 3: Implement `toCard()`**

Replace the `toCard()` method in `ExamCatalogQueryService.java`:

```java
    private PublicSubjectCardResponse toCard(ExamSubjectEntity s) {
        String published = ExamPaperStatus.PUBLISHED.dbValue();
        PublicSubjectCardResponse.LatestPaperSummary latestPaper = paperRepository
                .findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(s.getId(), published)
                .map(p -> new PublicSubjectCardResponse.LatestPaperSummary(
                        p.getExamCode(), p.getPaperType(), p.getCreatedAt()))
                .orElse(null);
        return new PublicSubjectCardResponse(
                s.getId(),
                s.getCode(),
                s.getTitle(),
                s.getCategorySlug(),
                s.getCardColor(),
                urlResolver.signed(s.getCoverImageUrl()),
                s.getViewCount(),
                (int) paperRepository.countBySubjectIdAndPaperTypeAndStatusAndDeletedAtIsNull(
                        s.getId(), ExamPaperType.FE.dbValue(), published),
                (int) paperRepository.countBySubjectIdAndPaperTypeAndStatusAndDeletedAtIsNull(
                        s.getId(), ExamPaperType.PE.dbValue(), published),
                s.getCurriculumTerm(),
                latestPaper);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && mvn -q -pl exam -am test`
Expected: PASS — full `exam` module test suite green (this also re-verifies every existing test that calls `listActive()`/`relatedCards()`/`getDetail()` transitively through `toCard()` still passes, since the two new fields don't change any existing field's value).

- [ ] **Step 5: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java
git commit -m "feat(exam): expose curriculum term and latest published paper on the public catalog"
```

---

### Task 4: Frontend types

**Files:**
- Modify: `Fuexam/lib/api/exam.ts`

**Interfaces:**
- Consumes: nothing new.
- Produces: `PublicSubjectCard.curriculumTerm: number | null`, `PublicSubjectCard.latestPaper: PublicSubjectLatestPaper | null`, new exported interface `PublicSubjectLatestPaper`. Consumed by Task 5.

- [ ] **Step 1: Extend the interface**

In `Fuexam/lib/api/exam.ts`, replace:

```ts
export interface PublicSubjectCard {
  id: string;
  code: string;
  title: string;
  categorySlug: string | null;
  cardColor: string | null;
  coverImageUrl: string | null;
  viewCount: number;
  fePaperCount: number;
  pePaperCount: number;
}
```

with:

```ts
export interface PublicSubjectLatestPaper {
  examCode: string;
  paperType: ExamPaperType;
  createdAt: string;
}

export interface PublicSubjectCard {
  id: string;
  code: string;
  title: string;
  categorySlug: string | null;
  cardColor: string | null;
  coverImageUrl: string | null;
  viewCount: number;
  fePaperCount: number;
  pePaperCount: number;
  curriculumTerm: number | null;
  latestPaper: PublicSubjectLatestPaper | null;
}
```

- [ ] **Step 2: Typecheck**

Run: `cd Fuexam && npx tsc --noEmit`
Expected: clean (this interface widening is additive; no existing consumer of `PublicSubjectCard` reads an exhaustive field list, so nothing breaks — `Fuexam/app/(app)/exam/page.tsx`'s current `SubjectCard` component only reads `coverUrl`/`code`/`title`/`cardColor`/`fePaperCount`/`pePaperCount` and is unaffected until Task 5 changes it).

- [ ] **Step 3: Commit**

```bash
git add Fuexam/lib/api/exam.ts
git commit -m "feat(exam-client): add curriculumTerm and latestPaper to PublicSubjectCard"
```

---

### Task 5: Restyle the client exam catalog page into the forum-tree layout

**Files:**
- Modify: `Fuexam/app/(app)/exam/page.tsx`

**Interfaces:**
- Consumes: `PublicSubjectCard.curriculumTerm` / `.latestPaper` / `.fePaperCount` / `.pePaperCount` (Task 4); `formatDateTime` from `@/lib/format-datetime` (pre-existing); `Collapsible`/`CollapsibleContent`/`CollapsibleTrigger` from `@/components/ui/collapsible` (pre-existing); `Badge` from `@/components/ui/badge` (pre-existing).
- Produces: nothing consumed by a later task — this is the last task.

- [ ] **Step 1: Replace the file**

Replace the full contents of `Fuexam/app/(app)/exam/page.tsx` with:

```tsx
"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { ChevronDown, Folder, FileStack } from "lucide-react";
import { ErrorBanner } from "@/components/ui/error-banner";
import { ApiError } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format-datetime";
import { type PublicSubjectCard, type PublicSubjectLatestPaper, listExamSubjects } from "@/lib/api/exam";

type TermGroup = {
  term: number | null;
  label: string;
  subjects: PublicSubjectCard[];
  feTotal: number;
  peTotal: number;
  latestPaper: (PublicSubjectLatestPaper & { subjectCode: string }) | null;
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

function groupSubjects(items: PublicSubjectCard[]): TermGroup[] {
  const byTerm = new Map<number | null, PublicSubjectCard[]>();
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
    const feTotal = subjects.reduce((sum, s) => sum + s.fePaperCount, 0);
    const peTotal = subjects.reduce((sum, s) => sum + s.pePaperCount, 0);
    const latestPaper = subjects.reduce<(PublicSubjectLatestPaper & { subjectCode: string }) | null>(
      (latest, s) => {
        if (!s.latestPaper) return latest;
        if (!latest || new Date(s.latestPaper.createdAt).getTime() > new Date(latest.createdAt).getTime()) {
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
      feTotal,
      peTotal,
      latestPaper,
    };
  });
}

function CardSkeleton() {
  return (
    <div className="flex flex-col gap-3 px-4 py-4">
      <div className="app-skeleton h-8 w-40 rounded" />
      <div className="app-skeleton h-4 w-full rounded" />
    </div>
  );
}

export default function ExamPage() {
  const [items, setItems] = useState<PublicSubjectCard[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await listExamSubjects());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách môn thi");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (item) =>
        item.code.toLowerCase().includes(q) || item.title.toLowerCase().includes(q),
    );
  }, [items, search]);

  const groups = useMemo(() => groupSubjects(filtered), [filtered]);

  return (
    <div className="space-y-8">
      <section className="space-y-4">
        <p className="app-eyebrow">Ngân hàng đề thi</p>
        <h1 className="font-display text-[clamp(2.5rem,6vw,4.5rem)] leading-[0.95]">
          Đề thi FE &amp; PE <br className="hidden sm:block" />theo mã môn
        </h1>
        <p className="max-w-xl text-lg text-muted-foreground">
          Luyện câu hỏi trắc nghiệm FE, tải đề thực hành PE và trao đổi cùng cộng đồng. Mở khóa toàn
          bộ với gói membership.
        </p>
      </section>

      <div className="sticky top-[88px] z-30 flex flex-wrap items-center gap-3 rounded-2xl border border-foreground/10 bg-background/80 p-3 backdrop-blur-xl">
        <Input
          className="h-10 min-w-[220px] flex-1"
          placeholder="Tìm theo mã môn — VD: PRF192, CSD201"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <section className="space-y-4">
        <ErrorBanner message={error} />

        <div className="rounded-2xl border border-foreground/10">
          {loading ? (
            <div className="divide-y divide-foreground/10">
              {Array.from({ length: 4 }).map((_, i) => <CardSkeleton key={i} />)}
            </div>
          ) : (
            <Collapsible defaultOpen>
              <div className="flex items-center justify-between border-b border-foreground/10 px-4 py-3">
                <div>
                  <p className="app-eyebrow">Danh sách môn</p>
                  <p className="font-mono text-xs text-muted-foreground">
                    {filtered.length} môn · nhóm theo kỳ học
                  </p>
                </div>
                <CollapsibleTrigger asChild>
                  <button
                    type="button"
                    aria-label="Thu gọn / mở rộng danh sách"
                    className="rounded-full p-2 text-muted-foreground hover:bg-foreground/5 hover:text-foreground"
                  >
                    <ChevronDown className="h-4 w-4" />
                  </button>
                </CollapsibleTrigger>
              </div>
              <CollapsibleContent>
                {filtered.length === 0 ? (
                  <div className="py-14 text-center">
                    <p className="font-medium">Không có môn phù hợp</p>
                    <p className="mt-1 text-sm text-muted-foreground">Thử từ khóa khác hoặc xóa bộ lọc.</p>
                  </div>
                ) : (
                  <div className="divide-y divide-foreground/10">
                    {groups.map((group) => (
                      <div
                        key={group.term ?? "none"}
                        className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-start"
                      >
                        <div className="flex shrink-0 items-center gap-2 sm:w-40">
                          {group.term === null ? (
                            <span className="flex h-8 w-8 items-center justify-center rounded-md bg-foreground/5 text-muted-foreground">
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
                            <Link
                              key={item.id}
                              href={`/exam/${item.code}`}
                              className="inline-flex items-center gap-1 font-mono text-sm text-sky-500 hover:underline"
                              title={item.title}
                            >
                              <Folder className="h-3.5 w-3.5 text-muted-foreground" />
                              {item.code}
                            </Link>
                          ))}
                        </div>

                        <div className="flex shrink-0 gap-6 text-right sm:w-28">
                          <div>
                            <p className="text-sm font-semibold">{group.feTotal}</p>
                            <p className="text-xs text-muted-foreground">Đề FE</p>
                          </div>
                          <div>
                            <p className="text-sm font-semibold">{group.peTotal}</p>
                            <p className="text-xs text-muted-foreground">Đề PE</p>
                          </div>
                        </div>

                        <div className="shrink-0 sm:w-56 sm:text-right">
                          {group.latestPaper ? (
                            <div className="flex flex-col items-start gap-1 sm:items-end">
                              <Badge
                                className={
                                  group.latestPaper.paperType === "FE"
                                    ? "bg-amber-500 text-amber-950 hover:bg-amber-500"
                                    : "bg-rose-500 text-rose-950 hover:bg-rose-500"
                                }
                              >
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
                  </div>
                )}
              </CollapsibleContent>
            </Collapsible>
          )}
        </div>
      </section>
    </div>
  );
}
```

- [ ] **Step 2: Typecheck**

Run: `cd Fuexam && npx tsc --noEmit`
Expected: clean.

- [ ] **Step 3: Production build**

Run: `cd Fuexam && npm run build`
Expected: succeeds, `/exam` route listed in the output same as before.

- [ ] **Step 4: Commit**

```bash
git add "Fuexam/app/(app)/exam/page.tsx"
git commit -m "feat(exam-client): render subjects grouped by curriculum term (forum-tree layout)"
```

---

## Plan Self-Review Notes

- Spec coverage: §2.2 (backend DTO/query) → Tasks 1-3; §2.3 (frontend grouping/render) → Tasks 4-5; §2.4 (out of scope items) → nothing in this plan touches them, confirmed by file list above.
- Placeholder scan: none found — every step has literal code or an exact `mvn`/`npx` command.
- Type/name consistency verified across tasks: `PublicSubjectCardResponse.curriculumTerm`/`.latestPaper` (Task 2) ↔ `card.curriculumTerm()`/`.latestPaper()` (Task 3 test) ↔ `toCard()`'s constructor call (Task 3 impl) ↔ `PublicSubjectCard.curriculumTerm`/`.latestPaper` (Task 4) ↔ `item.curriculumTerm`/`item.latestPaper` and `PublicSubjectLatestPaper` (Task 5) — same names and shapes throughout.
- The one existing caller of `new PublicSubjectCardResponse(...)` (`ExamCatalogQueryService.toCard()`) is the only call site needing an update (confirmed via `grep -rn "new PublicSubjectCardResponse(" backend/exam/src` before writing this plan — exactly one match), so no other file needs touching for the constructor-arity change.
