# Source Question Duplicate Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add frontend duplicate detection for question text and option text in the Fuexam-admin Source question editor, warning admins before they create/update a question that closely matches existing content.

**Architecture:** Two new utility/hook files (`text-similarity.ts`, `useDuplicateCheck.ts`) provide normalize + Levenshtein similarity + duplicate check logic. The existing questions page integrates the hook and shows a warning dialog via the existing AlertDialog UI primitives. No backend changes.

**Tech Stack:** Next.js (React), TypeScript, Radix AlertDialog (already in project)

## Global Constraints

- All user-facing strings in Vietnamese
- Similarity threshold: 0.8 (80%)
- Compare text-only content — skip image-only questions/options
- Warn only — never block submission
- No new npm dependencies

---

### Task 1: Text Similarity Utilities

**Files:**
- Create: `Fuexam-admin/lib/utils/text-similarity.ts`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `normalizeText(str: string | null | undefined): string`
  - `levenshteinSimilarity(a: string, b: string): number`

- [ ] **Step 1: Create `Fuexam-admin/lib/utils/text-similarity.ts` with `normalizeText`**

```typescript
export function normalizeText(str: string | null | undefined): string {
  if (!str) return "";
  return str
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-zA-Z0-9\s]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}
```

Behavior:
- `null` / `undefined` / `""` → `""`
- `"Java là gì??"` → `"java la gi"`
- `"  Hello   World!! "` → `"hello world"`
- `"Đáp án (A)"` → `"ap an a"`

- [ ] **Step 2: Add `levenshteinSimilarity` to the same file**

```typescript
function levenshteinDistance(a: string, b: string): number {
  if (a.length === 0) return b.length;
  if (b.length === 0) return a.length;

  if (a.length > b.length) [a, b] = [b, a];

  let prev = Array.from({ length: a.length + 1 }, (_, i) => i);
  let curr = new Array<number>(a.length + 1);

  for (let j = 1; j <= b.length; j++) {
    curr[0] = j;
    for (let i = 1; i <= a.length; i++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      curr[i] = Math.min(
        curr[i - 1] + 1,
        prev[i] + 1,
        prev[i - 1] + cost,
      );
    }
    [prev, curr] = [curr, prev];
  }

  return prev[a.length];
}

export function levenshteinSimilarity(a: string, b: string): number {
  const maxLen = Math.max(a.length, b.length);
  if (maxLen === 0) return 1;
  return 1 - levenshteinDistance(a, b) / maxLen;
}
```

Behavior:
- `("", "")` → `1`
- `("abc", "abc")` → `1`
- `("abc", "abd")` → `0.6667`
- `("kitten", "sitting")` → `0.5714`

- [ ] **Step 3: Verify manually by running the dev server**

Run:
```bash
cd Fuexam-admin && npx tsc --noEmit --pretty 2>&1 | head -20
```

Expected: no type errors in `text-similarity.ts`.

- [ ] **Step 4: Commit**

```bash
git add Fuexam-admin/lib/utils/text-similarity.ts
git commit -m "feat(admin): add normalizeText and levenshteinSimilarity utilities"
```

---

### Task 2: `useDuplicateCheck` Hook

**Files:**
- Create: `Fuexam-admin/hooks/useDuplicateCheck.ts`

**Interfaces:**
- Consumes:
  - `normalizeText(str: string | null | undefined): string` from `@/lib/utils/text-similarity`
  - `levenshteinSimilarity(a: string, b: string): number` from `@/lib/utils/text-similarity`
  - `AdminQuestion` from `@/lib/api/source` (type only)
  - `QuestionBody` from `@/lib/api/source` (type only)
- Produces:
  - `DuplicateMatch` interface — `{ existingQuestion: AdminQuestion; similarity: number }`
  - `OptionDuplicateMatch` interface — `{ newOptionText: string; existingQuestion: AdminQuestion; existingOptionText: string; similarity: number }`
  - `DuplicateResult` interface — `{ hasDuplicates: boolean; questionMatches: DuplicateMatch[]; optionMatches: OptionDuplicateMatch[] }`
  - `useDuplicateCheck(existingQuestions: AdminQuestion[], threshold?: number): { checkDuplicates: (newQuestion: QuestionBody, editingId?: string) => DuplicateResult }`

- [ ] **Step 1: Create `Fuexam-admin/hooks/useDuplicateCheck.ts`**

```typescript
import { useCallback } from "react";
import type { AdminQuestion, QuestionBody } from "@/lib/api/source";
import { normalizeText, levenshteinSimilarity } from "@/lib/utils/text-similarity";

export interface DuplicateMatch {
  existingQuestion: AdminQuestion;
  similarity: number;
}

export interface OptionDuplicateMatch {
  newOptionText: string;
  existingQuestion: AdminQuestion;
  existingOptionText: string;
  similarity: number;
}

export interface DuplicateResult {
  hasDuplicates: boolean;
  questionMatches: DuplicateMatch[];
  optionMatches: OptionDuplicateMatch[];
}

const DEFAULT_THRESHOLD = 0.8;

export function useDuplicateCheck(
  existingQuestions: AdminQuestion[],
  threshold: number = DEFAULT_THRESHOLD,
) {
  const checkDuplicates = useCallback(
    (newQuestion: QuestionBody, editingId?: string): DuplicateResult => {
      const questionMatches: DuplicateMatch[] = [];
      const optionMatches: OptionDuplicateMatch[] = [];

      const newQNorm = normalizeText(newQuestion.questionText);

      // Compare question text
      if (newQNorm) {
        for (const existing of existingQuestions) {
          if (existing.id === editingId) continue;
          const existQNorm = normalizeText(existing.questionText);
          if (!existQNorm) continue;
          const sim = levenshteinSimilarity(newQNorm, existQNorm);
          if (sim >= threshold) {
            questionMatches.push({ existingQuestion: existing, similarity: sim });
          }
        }
      }

      // Compare option texts
      const newOptionNorms = (newQuestion.options ?? [])
        .map((o) => normalizeText(o.optionText))
        .filter((t) => t.length > 0);

      for (const newOptNorm of newOptionNorms) {
        for (const existing of existingQuestions) {
          if (existing.id === editingId) continue;
          for (const existOpt of existing.options) {
            const existOptNorm = normalizeText(existOpt.optionText);
            if (!existOptNorm) continue;
            const sim = levenshteinSimilarity(newOptNorm, existOptNorm);
            if (sim >= threshold) {
              optionMatches.push({
                newOptionText: newOptNorm,
                existingQuestion: existing,
                existingOptionText: existOpt.optionText ?? "",
                similarity: sim,
              });
            }
          }
        }
      }

      return {
        hasDuplicates: questionMatches.length > 0 || optionMatches.length > 0,
        questionMatches,
        optionMatches,
      };
    },
    [existingQuestions, threshold],
  );

  return { checkDuplicates };
}
```

- [ ] **Step 2: Verify type correctness**

Run:
```bash
cd Fuexam-admin && npx tsc --noEmit --pretty 2>&1 | head -20
```

Expected: no type errors.

- [ ] **Step 3: Commit**

```bash
git add Fuexam-admin/hooks/useDuplicateCheck.ts
git commit -m "feat(admin): add useDuplicateCheck hook for fuzzy duplicate detection"
```

---

### Task 3: Integrate Duplicate Check into Questions Page

**Files:**
- Modify: `Fuexam-admin/app/source/catalog/[id]/questions/page.tsx`

**Interfaces:**
- Consumes:
  - `useDuplicateCheck(existingQuestions: AdminQuestion[], threshold?: number): { checkDuplicates }` from `@/hooks/useDuplicateCheck`
  - `DuplicateResult` from `@/hooks/useDuplicateCheck`
  - `AlertDialog*` components from `@/components/ui/alert-dialog` (already imported indirectly via `ConfirmDialog`)
- Produces: modified page with duplicate warning dialog

- [ ] **Step 1: Add imports at top of `questions/page.tsx`**

After the existing import of `ApiError`, add:

```typescript
import { useDuplicateCheck, type DuplicateResult } from "@/hooks/useDuplicateCheck";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
```

- [ ] **Step 2: Add state variables and hook call inside `AdminSourceQuestionsPage`**

After the existing `const [deleteId, setDeleteId] = ...` line (line 63), add:

```typescript
const [duplicateResult, setDuplicateResult] = useState<DuplicateResult | null>(null);
const [showDuplicateWarning, setShowDuplicateWarning] = useState(false);
const { checkDuplicates } = useDuplicateCheck(questions);
```

- [ ] **Step 3: Extract API save logic into a helper function**

Inside the component, add a new `saveQuestion` function that holds the existing API call logic (currently inside `handleSubmit` after validation). This function is called both from `handleSubmit` (when no duplicates) and from "Vẫn tạo" in the dialog.

```typescript
async function saveQuestion() {
  setSaving(true);
  try {
    if (editingId) {
      await updateQuestion(catalogItemId, editingId, buildBody());
      toast.success("Đã cập nhật câu hỏi.");
    } else {
      await createQuestion(catalogItemId, buildBody());
      toast.success("Đã tạo câu hỏi.");
    }
    resetForm();
    await load();
  } catch (err) {
    const message = err instanceof ApiError ? err.message : "Lưu thất bại.";
    setError(message);
    toast.error(message);
  } finally {
    setSaving(false);
  }
}
```

- [ ] **Step 4: Modify `handleSubmit` to check duplicates before saving**

Replace the `setSaving(true); try { ... } finally { setSaving(false); }` block at the end of the existing `handleSubmit` with:

```typescript
const result = checkDuplicates(buildBody(), editingId ?? undefined);
if (result.hasDuplicates) {
  setDuplicateResult(result);
  setShowDuplicateWarning(true);
  return;
}

await saveQuestion();
```

- [ ] **Step 5: Add `handleForceSubmit` function**

```typescript
async function handleForceSubmit() {
  setShowDuplicateWarning(false);
  setDuplicateResult(null);
  await saveQuestion();
}
```

- [ ] **Step 6: Add the duplicate warning dialog JSX**

After the existing `<ConfirmDialog>` for delete (before the closing `</AdminShell>`), add:

```tsx
<AlertDialog
  open={showDuplicateWarning}
  onOpenChange={(o) => {
    if (!o) {
      setShowDuplicateWarning(false);
      setDuplicateResult(null);
    }
  }}
>
  <AlertDialogContent className="max-h-[80vh] overflow-y-auto">
    <AlertDialogHeader>
      <AlertDialogTitle>Phát hiện nội dung trùng lặp</AlertDialogTitle>
      <AlertDialogDescription asChild>
        <div className="space-y-3 text-sm">
          {duplicateResult?.questionMatches.length ? (
            <div>
              <p className="font-semibold text-foreground">Câu hỏi trùng:</p>
              <ul className="mt-1 list-disc pl-5 space-y-1">
                {duplicateResult.questionMatches.map((m, i) => {
                  const idx = questions.findIndex((q) => q.id === m.existingQuestion.id) + 1;
                  const preview = (m.existingQuestion.questionText ?? "").slice(0, 60);
                  return (
                    <li key={i}>
                      Giống {Math.round(m.similarity * 100)}% với câu #{idx}:
                      &ldquo;{preview}{(m.existingQuestion.questionText?.length ?? 0) > 60 ? "…" : ""}&rdquo;
                    </li>
                  );
                })}
              </ul>
            </div>
          ) : null}
          {duplicateResult?.optionMatches.length ? (
            <div>
              <p className="font-semibold text-foreground">Đáp án trùng:</p>
              <ul className="mt-1 list-disc pl-5 space-y-1">
                {duplicateResult.optionMatches.map((m, i) => {
                  const idx = questions.findIndex((q) => q.id === m.existingQuestion.id) + 1;
                  return (
                    <li key={i}>
                      Đáp án &ldquo;{m.newOptionText.slice(0, 40)}&rdquo; giống{" "}
                      {Math.round(m.similarity * 100)}% với đáp án &ldquo;
                      {m.existingOptionText.slice(0, 40)}&rdquo; trong câu #{idx}
                    </li>
                  );
                })}
              </ul>
            </div>
          ) : null}
        </div>
      </AlertDialogDescription>
    </AlertDialogHeader>
    <AlertDialogFooter>
      <AlertDialogCancel>Quay lại sửa</AlertDialogCancel>
      <AlertDialogAction onClick={handleForceSubmit}>Vẫn tạo</AlertDialogAction>
    </AlertDialogFooter>
  </AlertDialogContent>
</AlertDialog>
```

- [ ] **Step 7: Verify type correctness and dev server**

Run:
```bash
cd Fuexam-admin && npx tsc --noEmit --pretty 2>&1 | head -30
```

Expected: no type errors.

Then start the dev server and manually test:
```bash
cd Fuexam-admin && npm run dev
```

Test scenarios:
1. Create a question with unique text → no dialog, submits normally
2. Create a question with text nearly identical to an existing question → warning dialog appears with question match
3. Create a question with an option text matching an existing option → warning dialog with option match
4. Click "Quay lại sửa" → dialog closes, form stays filled
5. Click "Vẫn tạo" → dialog closes, question is created
6. Edit an existing question → its own text should NOT trigger a self-match

- [ ] **Step 8: Commit**

```bash
git add Fuexam-admin/app/source/catalog/[id]/questions/page.tsx
git commit -m "feat(admin): integrate duplicate detection warning in question editor"
```
