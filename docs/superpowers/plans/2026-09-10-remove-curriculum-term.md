# Remove Curriculum Term, Group by Latest Paper Term Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the admin-assigned `curriculum_term` (0-9) concept entirely and replace subject-tree grouping (both admin and public client) with grouping by each subject's most recently created paper's academic term (`SU26`/`FA26`/...).

**Architecture:** Backend removal is one atomic task (entity, DTOs, services, every call site) since a half-removed threaded field compiles worse than either endpoint state. Frontend changes are two batched dispatches (types+page per app), reusing the exact grouping/labeling convention already established in the admin papers page's term grouping.

**Tech Stack:** Java 21 / Spring Boot 3.5.x (backend/exam module), Next.js App Router (Fuexam-admin + Fuexam).

**Spec:** `docs/superpowers/specs/2026-09-10-remove-curriculum-term-design.md`

## Global Constraints

- No DB foreign keys; migration is a new file `V57__drop_exam_subject_curriculum_term.sql` (highest existing is `V56`).
- `ExamSubjectEntity.create(...)` has 12 real call sites (grep-verified before writing this plan, across 6 files: `ExamSubjectAdminService.java`, `ExamPaperIngestService.java`, `ExamSubjectAdminServiceTest.java` ×4, `ExamCatalogQueryServiceTest.java` ×5, `ExamPaperIngestServiceTest.java` ×1). Removing its 8th parameter (`Integer curriculumTerm`, positioned right after `categorySlug`) is compile-enforced — a missed site is a build failure. Treat `mvn -q -pl exam -am compile` / `test` as the completeness gate.
- Do not touch `Fuexam-admin/app/exam/papers/page.tsx` or its `groupByTerm` — already correct, unrelated to this change.
- Do not touch `ExamPaperEntity`, the exam-code parser, or anything about how a paper's own `term`/`campus` is derived.
- New grouping label convention (must match exactly, for consistency with the existing admin-papers-page grouping): `label: term === null ? "Chưa rõ kỳ" : term` — no `"Kỳ " +` prefix.
- New grouping sort convention: `null` first, then ascending `localeCompare` — matches the admin-papers-page convention exactly.

---

### Task 1: Migration — drop `exam_subjects.curriculum_term`

**Files:**
- Create: `backend/app/src/main/resources/db/migration/V57__drop_exam_subject_curriculum_term.sql`

- [ ] **Step 1: Write the migration**

```sql
ALTER TABLE exam_subjects
    DROP COLUMN curriculum_term;
```

(Postgres drops the column's own `CHECK` constraint automatically along with the column — no separate `DROP CONSTRAINT` needed.)

- [ ] **Step 2: Verify it applies**

Run: `cd backend && mvn -q -pl app -am compile` (compiles; the real boot-time validation happens in Task 2's manual check).

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/main/resources/db/migration/V57__drop_exam_subject_curriculum_term.sql
git commit -m "feat(exam): drop exam_subjects.curriculum_term"
```

---

### Task 2: Backend — remove `curriculumTerm` everywhere, add `term` to latest-paper summaries

**Files:**
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamSubjectEntity.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/CreateSubjectRequest.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/UpdateSubjectRequest.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminSubjectResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectCardResponse.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamSubjectAdminService.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java`
- Modify: `backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamSubjectAdminServiceTest.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java`
- Modify: `backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java`

**Interfaces:**
- Consumes: `ExamPaperEntity.getTerm()` (pre-existing).
- Produces: `AdminSubjectResponse.LatestPaperSummary.term()`, `PublicSubjectCardResponse.LatestPaperSummary.term()` — consumed by Task 3 and Task 5 (frontend types).

- [ ] **Step 1: `ExamSubjectEntity.java`**

Remove the `curriculum_term` column field:
```java
    @Column(name = "curriculum_term")
    private Integer curriculumTerm;

```
(delete this whole block, right after the `categorySlug` field).

Remove the getter (`public Integer getCurriculumTerm() { return curriculumTerm; }`) and setter (`public void setCurriculumTerm(Integer curriculumTerm) { this.curriculumTerm = curriculumTerm; }`).

Change the `create(...)` factory back to 11 arguments (drop `Integer curriculumTerm` between `categorySlug` and `fePreviewImageCount`, and drop the `e.curriculumTerm = curriculumTerm;` assignment):
```java
    public static ExamSubjectEntity create(
            UUID id, String code, String title, String description,
            String coverImageUrl, String cardColor, String categorySlug,
            int fePreviewImageCount, boolean active, int sortOrder, Instant now) {
        ExamSubjectEntity e = new ExamSubjectEntity();
        e.id = id;
        e.code = code;
        e.title = title;
        e.description = description;
        e.coverImageUrl = coverImageUrl;
        e.cardColor = cardColor;
        e.categorySlug = categorySlug;
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

- [ ] **Step 2: `CreateSubjectRequest.java` / `UpdateSubjectRequest.java`**

Remove the `@Min(0) @Max(9) Integer curriculumTerm,` line from both records, and remove the now-unused `import jakarta.validation.constraints.Max;` from both files (confirm first that nothing else in either file still uses `@Max` — Global Constraints note the only other numeric field, `fePreviewImageCount`, uses only `@Min(0)`).

- [ ] **Step 3: `AdminSubjectResponse.java`**

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
    public record LatestPaperSummary(String examCode, String paperType, String status, String term, Instant createdAt) {
    }
}
```

- [ ] **Step 4: `PublicSubjectCardResponse.java`**

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
        LatestPaperSummary latestPaper
) {
    public record LatestPaperSummary(String examCode, String paperType, String term, Instant createdAt) {
    }
}
```

- [ ] **Step 5: `ExamSubjectAdminService.java`**

In `create(...)`, remove the `request.curriculumTerm(),` argument from the `ExamSubjectEntity.create(...)` call (it sat right after `blankToNull(request.categorySlug()),`).

In `update(...)`, remove this whole block:
```java
        if (request.curriculumTerm() != null) {
            entity.setCurriculumTerm(request.curriculumTerm());
        }
```

In `toAdmin(...)`, remove `e.getCurriculumTerm(),` from the `AdminSubjectResponse` construction (it sat right after `e.getCategorySlug(),`).

In `toLatestPaperSummary(...)`, add `paper.getTerm()` (right after `paper.getPaperType()`, before `paper.getStatus()`... actually match the record's declared field order — `LatestPaperSummary(String examCode, String paperType, String status, String term, Instant createdAt)` from Step 3, so `term` goes after `status`):
```java
    private static AdminSubjectResponse.LatestPaperSummary toLatestPaperSummary(ExamPaperEntity paper) {
        return new AdminSubjectResponse.LatestPaperSummary(
                paper.getExamCode(), paper.getPaperType(), paper.getStatus(), paper.getTerm(), paper.getCreatedAt());
    }
```

- [ ] **Step 6: `ExamCatalogQueryService.java`**

In `toCard(...)`, remove `s.getCurriculumTerm(),` from the final `PublicSubjectCardResponse` construction (it sat right after the two `countBySubjectIdAndPaperTypeAndStatusAndDeletedAtIsNull(...)` calls, right before `latestPaper`).

Add `p.getTerm()` to the inline `LatestPaperSummary` mapping (right after `p.getPaperType()`, matching the record's declared order from Step 4):
```java
        PublicSubjectCardResponse.LatestPaperSummary latestPaper = paperRepository
                .findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(s.getId(), published)
                .map(p -> new PublicSubjectCardResponse.LatestPaperSummary(
                        p.getExamCode(), p.getPaperType(), p.getTerm(), p.getCreatedAt()))
                .orElse(null);
```

- [ ] **Step 7: `ExamPaperIngestService.java`**

In `resolveOrCreateSubject(...)`, the `ExamSubjectEntity.create(...)` call currently reads:
```java
                    return subjectRepository.save(ExamSubjectEntity.create(
                            UUID.randomUUID(), subjectCode, subjectCode, null, null, null, null, null,
                            examProperties.defaultFePreviewImageCountOrDefault(), false, 0, now));
```
Remove one `null` (the `curriculumTerm` positional slot, 8th argument) so it becomes an 11-arg call:
```java
                    return subjectRepository.save(ExamSubjectEntity.create(
                            UUID.randomUUID(), subjectCode, subjectCode, null, null, null, null,
                            examProperties.defaultFePreviewImageCountOrDefault(), false, 0, now));
```

- [ ] **Step 8: Fix every remaining real call site of `ExamSubjectEntity.create(...)` and any test asserting `curriculumTerm`/the old `LatestPaperSummary` shape**

Run `cd backend && mvn -q -pl exam -am compile` — it fails at the remaining 9 real call sites (12 total minus the 3 already fixed in Steps 5/7) in `ExamSubjectAdminServiceTest.java`, `ExamCatalogQueryServiceTest.java`, and `ExamPaperIngestServiceTest.java`. Each currently passes 12 args with `curriculumTerm` in the 8th position (right after `categorySlug`, before `fePreviewImageCount`) — remove that one argument from each call. Then run `mvn -q -pl exam -am test` and fix any remaining compile error the same way, including:
- Any test that asserts on `card.curriculumTerm()` / `response.curriculumTerm()` — remove the assertion (the accessor no longer exists, so this is compile-enforced too).
- Any test that constructs `new AdminSubjectResponse.LatestPaperSummary(...)` or `new PublicSubjectCardResponse.LatestPaperSummary(...)` directly — add a `term` argument (e.g. `"SU26"` or `null`, whichever the test's own fixture data already implies) at the position matching Steps 3/4's declared field order.
- `ExamSubjectAdminServiceTest.java` has a test named something like `listAllReportsPaperCountsAndLatestPaperRegardlessOfStatus` (or similar) that may assert on `latestPaper().paperType()` — if it also should assert `term`, add `assertEquals(<the fixture's term value>, response.latestPaper().term())` for stronger coverage, but this is optional polish, not required for the task to be done.

Do not stop until `mvn -q -pl exam -am test` is clean.

- [ ] **Step 9: Commit**

```bash
git add backend/exam/src/main/java/com/fuoverflow/exam/persistence/ExamSubjectEntity.java \
        backend/exam/src/main/java/com/fuoverflow/exam/api/dto/CreateSubjectRequest.java \
        backend/exam/src/main/java/com/fuoverflow/exam/api/dto/UpdateSubjectRequest.java \
        backend/exam/src/main/java/com/fuoverflow/exam/api/dto/AdminSubjectResponse.java \
        backend/exam/src/main/java/com/fuoverflow/exam/api/dto/PublicSubjectCardResponse.java \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamSubjectAdminService.java \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamCatalogQueryService.java \
        backend/exam/src/main/java/com/fuoverflow/exam/application/ExamPaperIngestService.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamSubjectAdminServiceTest.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamCatalogQueryServiceTest.java \
        backend/exam/src/test/java/com/fuoverflow/exam/application/ExamPaperIngestServiceTest.java
git commit -m "feat(exam): remove curriculumTerm, add term to subject latest-paper summaries"
```

- [ ] **Step 10: Boot the app for real against the dropped column**

Run the app locally with `--spring.profiles.active=local` against a real Postgres (same procedure used throughout this series). Confirm Hibernate's schema validation passes with the column gone (no leftover mapped field referencing it). Stop the app afterward.

---

### Task 3-4 (batched): Fuexam-admin — types, then subject-tree page grouped by latest-paper term

**Files:**
- Modify: `Fuexam-admin/lib/api/exam.ts`
- Modify: `Fuexam-admin/app/exam/subjects/page.tsx`

**Interfaces:**
- Consumes: `AdminSubjectResponse.LatestPaperSummary.term` (Task 2).
- Produces: nothing consumed by a later task.

- [ ] **Step 1: `Fuexam-admin/lib/api/exam.ts`**

Change `AdminSubjectLatestPaper` (add `term`):
```ts
export interface AdminSubjectLatestPaper {
  examCode: string;
  paperType: ExamPaperType;
  status: ExamPaperStatus;
  term: string | null;
  createdAt: string;
}
```

Remove `curriculumTerm: number | null;` from `AdminSubject`.

Remove `curriculumTerm?: number | null;` from `AdminSubjectBody`.

- [ ] **Step 2: Typecheck**

Run: `cd Fuexam-admin && npx tsc --noEmit` — expected to FAIL at this point (the page still references `curriculumTerm`); this is expected, Step 3 fixes it.

- [ ] **Step 3: Replace `Fuexam-admin/app/exam/subjects/page.tsx`**

Replace the full file contents with:

```tsx
"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { ImageUploader } from "@/components/admin/ImageUploader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { ChevronDown, Folder, FileStack } from "lucide-react";
import { formatDateTime } from "@/lib/format-datetime";
import {
  type AdminSubject,
  type AdminSubjectLatestPaper,
  createExamSubject,
  deleteExamSubject,
  listExamSubjects,
  updateExamSubject,
} from "@/lib/api/exam";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

const EMPTY_FORM = {
  code: "",
  title: "",
  description: "",
  coverImageUrl: "",
  cardColor: "",
  categorySlug: "on-thi",
  active: true,
  sortOrder: "0",
  fePreviewImageCount: "2",
};

type TermGroup = {
  term: string | null;
  label: string;
  subjects: AdminSubject[];
  feQuestionTotal: number;
  paperTotal: number;
  latestPaper: (AdminSubjectLatestPaper & { subjectCode: string }) | null;
};

/** Cycles through a fixed palette so each term badge gets a distinct, stable color. */
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

function termBadgeColor(term: string): string {
  let hash = 0;
  for (let i = 0; i < term.length; i++) {
    hash = (hash * 31 + term.charCodeAt(i)) % TERM_BADGE_COLORS.length;
  }
  return TERM_BADGE_COLORS[hash];
}

function groupSubjects(items: AdminSubject[]): TermGroup[] {
  const byTerm = new Map<string | null, AdminSubject[]>();
  for (const item of items) {
    const key = item.latestPaper?.term ?? null;
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
    return a.localeCompare(b);
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
      label: term === null ? "Chưa rõ kỳ" : term,
      subjects,
      feQuestionTotal,
      paperTotal,
      latestPaper,
    };
  });
}

export default function AdminExamSubjectsPage() {
  const { user } = useAuth();
  const canWrite =
    can(user, "exam.subject.admin:create") || can(user, "exam.subject.admin:update");
  const canDelete = can(user, "exam.subject.admin:delete");

  const [items, setItems] = useState<AdminSubject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState("");
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listExamSubjects());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được môn thi.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function resetForm() {
    setForm(EMPTY_FORM);
    setEditingId(null);
  }

  function startEdit(item: AdminSubject) {
    setEditingId(item.id);
    setForm({
      code: item.code,
      title: item.title,
      description: item.description ?? "",
      coverImageUrl: item.coverImageUrl ?? "",
      cardColor: item.cardColor ?? "",
      categorySlug: item.categorySlug ?? "",
      active: item.active,
      sortOrder: String(item.sortOrder),
      fePreviewImageCount: String(item.fePreviewImageCount),
    });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSaving(true);
    const body = {
      code: form.code.trim(),
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      coverImageUrl: form.coverImageUrl || undefined,
      cardColor: form.cardColor.trim() || undefined,
      categorySlug: form.categorySlug.trim() || undefined,
      active: form.active,
      sortOrder: Number.parseInt(form.sortOrder, 10) || 0,
      fePreviewImageCount: Number.parseInt(form.fePreviewImageCount, 10) || 2,
    };
    try {
      if (editingId) {
        const updated = await updateExamSubject(editingId, body);
        setItems((prev) => prev.map((it) => (it.id === updated.id ? updated : it)));
        toast.success("Đã cập nhật môn thi.");
      } else {
        const created = await createExamSubject(body);
        setItems((prev) => [...prev, created]);
        toast.success("Đã tạo môn thi.");
      }
      resetForm();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Lưu thất bại.";
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }

  async function confirmDelete() {
    if (!deleteId) return;
    try {
      await deleteExamSubject(deleteId);
      setItems((prev) => prev.filter((it) => it.id !== deleteId));
      toast.success("Đã xóa môn thi.");
      if (editingId === deleteId) resetForm();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setDeleteId(null);
    }
  }

  const filteredItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (item) =>
        item.code.toLowerCase().includes(q) ||
        item.title.toLowerCase().includes(q) ||
        (item.categorySlug ?? "").toLowerCase().includes(q),
    );
  }, [items, search]);

  const groups = useMemo(() => groupSubjects(filteredItems), [filteredItems]);

  return (
    <AdminShell
      title="Exam FE/PE — Môn thi"
      description="Quản lý môn thi và các đề thi (FE/PE) kèm tài liệu"
    >
      <div className="mb-4 max-w-md">
        <Input
          placeholder="Tìm theo mã, tên hoặc danh mục..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {search.trim() && (
          <p className="mt-1 text-xs text-muted-foreground">
            {filteredItems.length} / {items.length} môn thi
          </p>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {canWrite && (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">
                {editingId ? "Sửa môn thi" : "Thêm môn thi"}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit} className="space-y-4">
                {error && (
                  <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                    {error}
                  </div>
                )}
                <div className="space-y-2">
                  <Label htmlFor="code">Mã môn</Label>
                  <Input
                    id="code"
                    placeholder="PRF192"
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    disabled={!!editingId}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="title">Tên môn thi</Label>
                  <Input
                    id="title"
                    value={form.title}
                    onChange={(e) => setForm({ ...form, title: e.target.value })}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="desc">Mô tả</Label>
                  <Textarea
                    id="desc"
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                  />
                </div>
                <ImageUploader
                  label="Ảnh bìa"
                  purpose="exam_paper_image"
                  value={form.coverImageUrl || null}
                  onChange={(url) => setForm({ ...form, coverImageUrl: url ?? "" })}
                />
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="color">Màu thẻ (hex)</Label>
                    <Input
                      id="color"
                      placeholder="#6d28d9"
                      value={form.cardColor}
                      onChange={(e) => setForm({ ...form, cardColor: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="cat">Danh mục (slug)</Label>
                    <Input
                      id="cat"
                      value={form.categorySlug}
                      onChange={(e) => setForm({ ...form, categorySlug: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="sort">Thứ tự</Label>
                    <Input
                      id="sort"
                      type="number"
                      min={0}
                      value={form.sortOrder}
                      onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="fePreviewImageCount">Số ảnh xem trước (free user)</Label>
                    <Input
                      id="fePreviewImageCount"
                      type="number"
                      min={1}
                      max={10}
                      value={form.fePreviewImageCount}
                      onChange={(e) => setForm({ ...form, fePreviewImageCount: e.target.value })}
                    />
                  </div>
                </div>
                <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2">
                  <Label htmlFor="active" className="cursor-pointer">
                    Đang hiển thị
                  </Label>
                  <Switch
                    id="active"
                    checked={form.active}
                    onCheckedChange={(v) => setForm({ ...form, active: v })}
                  />
                </div>
                <div className="flex gap-2">
                  <Button type="submit" disabled={saving}>
                    {saving ? "Đang lưu..." : editingId ? "Cập nhật" : "Tạo mới"}
                  </Button>
                  {editingId && (
                    <Button type="button" variant="outline" onClick={resetForm}>
                      Hủy
                    </Button>
                  )}
                </div>
              </form>
            </CardContent>
          </Card>
        )}

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
                      {items.length} môn · nhóm theo kỳ thi gần nhất
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
                      <div key={group.term ?? "none"} className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-start">
                        <div className="flex shrink-0 items-center gap-2 sm:w-40">
                          {group.term === null ? (
                            <span className="flex h-8 w-8 items-center justify-center rounded-md bg-muted text-muted-foreground">
                              <FileStack className="h-4 w-4" />
                            </span>
                          ) : (
                            <span
                              className={`flex h-8 items-center justify-center rounded-full px-2 text-xs font-bold ${termBadgeColor(group.term)}`}
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
                              {!item.active && (
                                <span className="rounded bg-muted px-1 text-[10px] font-medium text-muted-foreground">
                                  Ẩn
                                </span>
                              )}
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
      </div>

      <ConfirmDialog
        open={deleteId != null}
        title="Xóa môn thi?"
        description="Hành động này không thể hoàn tác."
        destructive
        confirmLabel="Xóa"
        onConfirm={confirmDelete}
        onOpenChange={(o) => !o && setDeleteId(null)}
      />
    </AdminShell>
  );
}
```

- [ ] **Step 4: Typecheck and build**

Run: `cd Fuexam-admin && npx tsc --noEmit` — clean.
Run: `cd Fuexam-admin && npm run build` — succeeds, `/exam/subjects` still a route.

- [ ] **Step 5: Commit**

```bash
git add Fuexam-admin/lib/api/exam.ts Fuexam-admin/app/exam/subjects/page.tsx
git commit -m "feat(exam-admin): group subjects by latest paper term instead of curriculumTerm"
```

---

### Task 5-6 (batched): Fuexam client — types, then subject-tree page grouped by latest-paper term

**Files:**
- Modify: `Fuexam/lib/api/exam.ts`
- Modify: `Fuexam/app/(app)/exam/page.tsx`

**Interfaces:**
- Consumes: `PublicSubjectCardResponse.LatestPaperSummary.term` (Task 2).
- Produces: nothing consumed by a later task — this is the last task.

- [ ] **Step 1: `Fuexam/lib/api/exam.ts`**

Change `PublicSubjectLatestPaper` (add `term`):
```ts
export interface PublicSubjectLatestPaper {
  examCode: string;
  paperType: ExamPaperType;
  term: string | null;
  createdAt: string;
}
```

Remove `curriculumTerm: number | null;` from `PublicSubjectCard`.

- [ ] **Step 2: Typecheck**

Run: `cd Fuexam && npx tsc --noEmit` — expected to FAIL at this point (the page still references `curriculumTerm`); Step 3 fixes it.

- [ ] **Step 3: Replace `Fuexam/app/(app)/exam/page.tsx`**

Replace the full file contents with:

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
  term: string | null;
  label: string;
  subjects: PublicSubjectCard[];
  feTotal: number;
  peTotal: number;
  latestPaper: (PublicSubjectLatestPaper & { subjectCode: string }) | null;
};

/** Cycles through a fixed palette so each term badge gets a distinct, stable color. */
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

function termBadgeColor(term: string): string {
  let hash = 0;
  for (let i = 0; i < term.length; i++) {
    hash = (hash * 31 + term.charCodeAt(i)) % TERM_BADGE_COLORS.length;
  }
  return TERM_BADGE_COLORS[hash];
}

function groupSubjects(items: PublicSubjectCard[]): TermGroup[] {
  const byTerm = new Map<string | null, PublicSubjectCard[]>();
  for (const item of items) {
    const key = item.latestPaper?.term ?? null;
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
    return a.localeCompare(b);
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
      label: term === null ? "Chưa rõ kỳ" : term,
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
                    {filtered.length} môn · nhóm theo kỳ thi gần nhất
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
                              className={`flex h-8 items-center justify-center rounded-full px-2 text-xs font-bold ${termBadgeColor(group.term)}`}
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

- [ ] **Step 4: Typecheck and build**

Run: `cd Fuexam && npx tsc --noEmit` — clean (only the 6 pre-existing, unrelated `SourceQuestionRunner.tsx` errors already known from a prior branch in this series may appear — confirmed harmless there, not this task's concern).
Run: `cd Fuexam && npm run build` — succeeds, `/exam` still a route.

- [ ] **Step 5: Commit**

```bash
git add Fuexam/lib/api/exam.ts "Fuexam/app/(app)/exam/page.tsx"
git commit -m "feat(exam-client): group subjects by latest paper term instead of curriculumTerm"
```

---

## Plan Self-Review Notes

- Spec coverage: §2.1 (migration) → Task 1; §2.2 (backend) → Task 2; §2.3 (frontend, both apps) → Tasks 3-4 and 5-6; §2.4 (out of scope) → nothing in this plan touches those files.
- Placeholder scan: none — every step has literal code or an exact command; Task 2 Step 8's "fix the remaining call sites" instruction is backed by a grep-verified exact count (12) and a compile-driven completeness gate, matching the pattern already used successfully in this session.
- Type/name consistency verified across tasks: `LatestPaperSummary.term` (Task 2, both DTOs) → `AdminSubjectLatestPaper.term`/`PublicSubjectLatestPaper.term` (Tasks 3/5) → `item.latestPaper?.term` read in both pages (Tasks 4/6) — same name throughout. `TermGroup.term: string | null` and `termBadgeColor(term: string)` consistent between both frontend apps (intentionally near-identical, matching how the two apps' subject trees have tracked each other throughout this series).
- Every real call site of `ExamSubjectEntity.create(...)` was grep-counted before writing this plan (12) and Task 2 states the exact number so an implementer can self-verify against `mvn compile`/`test`.
