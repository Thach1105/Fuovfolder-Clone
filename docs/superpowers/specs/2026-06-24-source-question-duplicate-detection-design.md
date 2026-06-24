# Source Question Duplicate Detection — Frontend Validation

## Summary

Add client-side duplicate detection when creating/updating questions in the admin Source question editor. Before submitting to the API, compare the new question text and option texts against all existing questions in the same source using fuzzy matching (Levenshtein similarity). Show a warning dialog listing duplicates; admin can choose to proceed or go back and edit.

## Context

- Source = purchasable exam question bank (`source_catalog_items`)
- Questions = `source_questions` with `questionText` (text) and/or images
- Options (answers) = `source_question_options` with `optionText` (text) and/or images
- Admin page: `Fuexam-admin/app/source/catalog/[id]/questions/page.tsx`
- Existing questions are already loaded in component state via `listAdminQuestions()`

## Requirements

1. Compare **text-only** — skip questions/options that only have images, no URLs
2. Normalize before comparison: strip Vietnamese diacritics, remove special characters, collapse whitespace, lowercase
3. Use Levenshtein distance for fuzzy matching with similarity threshold of 80%
4. Compare both question text and option texts independently
5. When editing, exclude the question being edited from comparisons
6. On duplicate detection: show warning dialog listing all matches, allow "Vẫn tạo" (proceed) or "Quay lại sửa" (cancel)
7. Do not block submission — warn only

## Architecture

### New files

| File | Purpose |
|---|---|
| `Fuexam-admin/lib/utils/text-similarity.ts` | `normalizeText()` and `levenshteinSimilarity()` utilities |
| `Fuexam-admin/hooks/useDuplicateCheck.ts` | Custom hook encapsulating duplicate detection logic |

### Modified files

| File | Changes |
|---|---|
| `Fuexam-admin/app/source/catalog/[id]/questions/page.tsx` | Integrate hook, add warning dialog, modify `handleSubmit` flow |

## Detailed Design

### 1. Text Normalization — `normalizeText(str)`

```
Input:  "Java là gì??"
Step 1: NFD decompose → strip combining marks (remove diacritics)  → "Java la gi??"
Step 2: Remove non-alphanumeric except spaces                      → "Java la gi"
Step 3: Collapse multiple spaces to one                            → "Java la gi"
Step 4: Trim + lowercase                                           → "java la gi"
```

Returns empty string for null/undefined input.

### 2. Levenshtein Similarity — `levenshteinSimilarity(a, b)`

- Standard Levenshtein edit distance with dynamic programming (O(m*n) space-optimized to O(min(m,n)))
- Returns: `1 - (distance / max(a.length, b.length))`
- Range: 0 (completely different) to 1 (identical)
- If both strings are empty, returns 1

### 3. Custom Hook — `useDuplicateCheck`

```typescript
interface DuplicateMatch {
  existingQuestion: AdminQuestion;
  similarity: number;
}

interface OptionDuplicateMatch {
  newOptionText: string;
  existingQuestion: AdminQuestion;
  existingOptionText: string;
  similarity: number;
}

interface DuplicateResult {
  hasDuplicates: boolean;
  questionMatches: DuplicateMatch[];
  optionMatches: OptionDuplicateMatch[];
}

function useDuplicateCheck(
  existingQuestions: AdminQuestion[],
  threshold?: number  // default 0.8
): {
  checkDuplicates: (newQuestion: QuestionBody, editingId?: string) => DuplicateResult;
}
```

**`checkDuplicates` logic:**

1. Normalize `newQuestion.questionText` → `newQNorm`
2. If `newQNorm` is non-empty, iterate `existingQuestions`:
   - Skip if `question.id === editingId`
   - Skip if `question.questionText` is null/empty
   - Normalize existing question text → `existQNorm`
   - Compute `levenshteinSimilarity(newQNorm, existQNorm)`
   - If >= threshold → add to `questionMatches`
3. Collect new option texts: for each option in `newQuestion.options`, normalize `optionText` → skip if empty (image-only)
4. For each normalized new option, iterate all options of all existing questions:
   - Skip if existing option has no text (image-only)
   - Skip if parent question.id === editingId
   - Normalize existing option text
   - Compute similarity
   - If >= threshold → add to `optionMatches`
5. Return `{ hasDuplicates: questionMatches.length > 0 || optionMatches.length > 0, questionMatches, optionMatches }`

### 4. UI Integration — `questions/page.tsx`

**New state:**
```typescript
const [duplicateResult, setDuplicateResult] = useState<DuplicateResult | null>(null);
const [showDuplicateWarning, setShowDuplicateWarning] = useState(false);
```

**Modified `handleSubmit` flow:**
```
handleSubmit(e)
  → existing validation (empty options, correct answer, text/image)
  → const result = checkDuplicates(buildBody(), editingId)
  → if result.hasDuplicates:
      setDuplicateResult(result)
      setShowDuplicateWarning(true)
      return  // stop, show dialog
  → else: proceed to API call (existing logic)
```

**New function `handleForceSubmit`:**
```
handleForceSubmit()
  → setShowDuplicateWarning(false)
  → proceed to API call (same create/update logic)
```

**Warning dialog content:**
- Title: "Phát hiện nội dung trùng lặp"
- Sections:
  - "Câu hỏi trùng:" — list each match: `"Giống {XX}% với câu {#index}: '{preview truncated to 60 chars}'"`
  - "Đáp án trùng:" — list each match: `"Đáp án '{new}' giống {XX}% với đáp án '{existing}' trong câu {#index}"`
- Buttons: "Quay lại sửa" (close dialog) | "Vẫn tạo" (call `handleForceSubmit`)

## Performance

- Existing questions are already in memory (loaded by `listAdminQuestions`)
- Levenshtein is O(m*n) per pair; for a source with 200 questions and 4 options each = ~800 comparisons for options, ~200 for question text
- Text lengths are typically short (< 200 chars) — negligible computation time
- No additional API calls needed

## Not in scope

- Backend duplicate detection
- Image-based duplicate detection
- Cross-source duplicate detection
- Auto-suggest corrections for duplicates
