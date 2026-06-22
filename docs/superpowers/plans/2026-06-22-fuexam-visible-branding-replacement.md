# Fuexam Visible Branding Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all user-visible old-brand text with `Fuexam`/`Fuexam Point` across frontend UI, admin UI, visible metadata, and user-facing backend copy without renaming non-visible internal technical identifiers.

**Architecture:** The change is a scoped copy/metadata sweep grouped by rendering surface. Each task first proves the target strings exist with focused search/build checks, then updates only visible literals while leaving package names, CSS token names, API shapes, and other internal identifiers untouched.

**Tech Stack:** Next.js App Router, React, TypeScript, Tailwind CSS, Spring Boot 3.5.x, Maven, ripgrep

## Global Constraints

- Replace visible variants of the old brand with `Fuexam`.
- Replace visible `FUO Point` with `Fuexam Point`.
- Change only text that is actually visible to users.
- Do not rename package names, Java class names, imports, database names, columns, enums, migrations, internal constants, internal IDs, Maven coordinates, module names, or other technical identifiers that are not rendered to users.
- Do not automatically change actual working destinations, endpoints, or configured sender values when a domain/email value is functional infrastructure rather than display text.
- Keep the change focused on visible branding only.
- Preserve existing behavior except for the approved visible branding updates.
- Prefer the smallest useful verification commands after implementation.

---

## File Structure / Responsibility Map

### Main frontend brand surfaces
- Modify: `Fuexam/app/layout.tsx` — root metadata/title/description/generator visible in browser/platform surfaces.
- Modify: `Fuexam/app/page.tsx` — landing-page visible copy including `FUO Point` mentions.
- Modify: `Fuexam/app/admin/layout.tsx` — embedded admin title if rendered from main app entry.
- Modify: `Fuexam/app/(app)/**/page.tsx` where literal old-brand text is rendered.
- Modify: `Fuexam/components/**` only where old-brand literals are visible to users.
- Avoid semantic rename of CSS token names in `Fuexam/app/globals.css`; token names like `fuo-600` are internal styling identifiers, not user copy.

### Admin frontend brand surfaces
- Modify: `Fuexam-admin/app/layout.tsx` — admin metadata/title/description.
- Modify: `Fuexam-admin/app/source/catalog/page.tsx` — admin visible labels/descriptions containing `FUO Point`.
- Modify: `Fuexam-admin/app/source/purchases/page.tsx` — admin visible descriptions/refund dialogs/labels containing `FUO Point`.
- Modify: other `Fuexam-admin/app/**/page.tsx` and `Fuexam-admin/components/**` only where literal old-brand text is user-visible.
- Keep type/interface names in `Fuexam-admin/lib/api/points.ts` and similar files unchanged unless the literal string itself is rendered.

### Backend visible-copy surfaces
- Inspect: `backend/**/src/main/resources/**` for mail/templates/messages.
- Inspect: `backend/**/src/main/java/**` for user-facing literals in notification subjects/messages.
- Modify only files with visible copy; skip `pom.xml`, package declarations, Docker user/group names, and scripts because they are non-visible technical identifiers.

### Verification surfaces
- Use repository search to confirm no visible old-brand literals remain.
- Use frontend package scripts in `Fuexam/package.json` and `Fuexam-admin/package.json` for the smallest useful validation.
- Use `cd backend && mvn -q -pl notification -am test` only if backend visible-copy files change inside notification/mail-related modules.

---

### Task 1: Inventory all visible old-brand occurrences

**Files:**
- Modify: none
- Inspect: `Fuexam/app/layout.tsx`
- Inspect: `Fuexam/app/page.tsx`
- Inspect: `Fuexam-admin/app/layout.tsx`
- Inspect: `Fuexam-admin/app/source/catalog/page.tsx`
- Inspect: `Fuexam-admin/app/source/purchases/page.tsx`
- Inspect: `backend/**/src/main/resources/**`
- Inspect: `backend/**/src/main/java/**`

**Interfaces:**
- Consumes: approved spec at `docs/superpowers/specs/2026-06-22-fuexam-visible-branding-design.md`
- Produces: categorized hit list with buckets `ui`, `admin-ui`, `metadata`, `mail`, `user-message`, `internal-ignore`

- [ ] **Step 1: Search main frontend for visible brand literals**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" app components
```

Expected: hits in visible copy files such as `app/page.tsx`; styling-token hits like `text-fuo-600` may also appear and must be tagged `internal-ignore`.

- [ ] **Step 2: Search admin frontend for visible brand literals**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam-admin
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" app components
```

Expected: hits in admin metadata and admin Source pages, plus possible internal-uppercase role names that need manual classification.

- [ ] **Step 3: Search backend for user-facing brand literals**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/backend
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" . --glob '!**/target/**'
```

Expected: mostly internal identifiers; only retain hits from templates/messages/notification copy for later tasks.

- [ ] **Step 4: Create the working inventory in the task notes or scratchpad**

```text
ui:
- Fuexam/app/page.tsx:44 -> "FUO Point"

admin-ui:
- Fuexam-admin/app/source/catalog/page.tsx:224 -> "FUO Point"
- Fuexam-admin/app/source/purchases/page.tsx:113 -> "FUO Point"

metadata:
- Fuexam/app/layout.tsx:24-27
- Fuexam-admin/app/layout.tsx:12-14

internal-ignore:
- Fuexam/app/globals.css:* -> color token names like --color-fuo-600
- backend/**/pom.xml -> Maven coordinates
```

Expected: every remaining hit has a category and a reason to edit or ignore.

- [ ] **Step 5: Commit the inventory checkpoint**

```bash
git status --short
# No commit required if no file changed yet; proceed directly to Task 2.
```

Expected: no code changes yet.

---

### Task 2: Update main frontend metadata and landing-page copy

**Files:**
- Modify: `Fuexam/app/layout.tsx`
- Modify: `Fuexam/app/page.tsx`
- Test: `Fuexam/package.json`

**Interfaces:**
- Consumes: Task 1 inventory bucket `metadata` and `ui`
- Produces: visible root-brand copy uses `Fuexam`/`Fuexam Point`; root metadata remains valid `Metadata`

- [ ] **Step 1: Write a focused failing check for old-brand copy in root frontend files**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" Fuexam/app/layout.tsx Fuexam/app/page.tsx
```

Expected: FAIL condition for the final state because this command should currently print old-brand hits.

- [ ] **Step 2: Update root metadata in `Fuexam/app/layout.tsx`**

```ts
export const metadata: Metadata = {
  title: 'Fuexam — Cộng đồng sinh viên FPT',
  description: 'Diễn đàn, tài liệu ôn thi (Source) và khóa học cho sinh viên FPT.',
  generator: 'Fuexam',
}
```

Expected: visible browser/platform branding uses `Fuexam` casing consistently.

- [ ] **Step 3: Update landing-page visible copy in `Fuexam/app/page.tsx`**

```ts
const STEPS = [
  {
    number: "II",
    title: "Chọn Source theo mã môn",
    desc: "Tìm tài liệu ôn thi theo mã môn (MLN111, CSI106...), xem tỉ lệ trùng lặp đề và mở khóa bằng Fuexam Point.",
  },
];

const PLANS = [
  {
    name: "Miễn phí",
    price: "0",
    unit: "Fuexam Point",
    description: "Bắt đầu với cộng đồng và tài liệu cơ bản.",
    features: ["Truy cập diễn đàn", "Xem tài liệu công khai", "Tích điểm Fuexam Point"],
  },
];

<p className="mt-5 max-w-xl text-muted-foreground">
  Dùng Fuexam Point để mở Source theo môn hoặc nâng cấp membership. Không phí ẩn.
</p>
```

Expected: all visible point-brand mentions on the landing page use `Fuexam Point`.

- [ ] **Step 4: Re-run the focused check**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" Fuexam/app/layout.tsx Fuexam/app/page.tsx
```

Expected: no output, except acceptable internal-only styling-token hits if the file still contains class/token names unrelated to visible copy.

- [ ] **Step 5: Run the smallest frontend validation**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam
cat package.json
```

Use the script names found there, then run the smallest available validation command, preferring one of:

```bash
pnpm lint
# or
pnpm typecheck
# or
pnpm build
```

Expected: command exits 0.

- [ ] **Step 6: Commit**

```bash
git add Fuexam/app/layout.tsx Fuexam/app/page.tsx
git commit -m "feat: update main frontend Fuexam branding"
```

Expected: one commit covering root metadata and landing-page copy only.

---

### Task 3: Update remaining visible copy in the main frontend app

**Files:**
- Modify: `Fuexam/app/**/page.tsx` where visible old-brand literals remain
- Modify: `Fuexam/components/**` where visible old-brand literals remain
- Test: `Fuexam/package.json`

**Interfaces:**
- Consumes: Task 1 inventory bucket `ui`
- Produces: no visible old-brand literals remain in the main frontend app; internal styling tokens like `text-fuo-600` remain untouched if non-visible

- [ ] **Step 1: Re-search only the main frontend app after Task 2**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" app components
```

Expected: remaining hits are a short list that can be classified as visible-copy edits vs internal styling tokens.

- [ ] **Step 2: Edit only visible literals in remaining main-app files**

```text
Examples of allowed edits:
- "FUO Point" -> "Fuexam Point"
- "FuOverflow" -> "Fuexam"
- visible admin/page title strings containing old brand -> use "Fuexam"

Examples of forbidden edits:
- className="text-fuo-600"
- CSS variable names like --color-fuo-600
- helper/type names that are not rendered to users
```

Expected: visible literals updated, internal identifiers preserved.

- [ ] **Step 3: Run a no-visible-old-brand check for the main frontend**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" app components | rg -v "text-fuo-|bg-fuo-|border-fuo-|--color-fuo-"
```

Expected: no output.

- [ ] **Step 4: Run the same smallest useful frontend validation command from Task 2**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam
pnpm lint
# or the validated alternative discovered in Task 2
```

Expected: exits 0.

- [ ] **Step 5: Commit**

```bash
git add Fuexam/app Fuexam/components
git commit -m "feat: replace remaining main app visible branding"
```

Expected: one commit for remaining main-app visible-copy edits.

---

### Task 4: Update admin frontend metadata and visible point-brand copy

**Files:**
- Modify: `Fuexam-admin/app/layout.tsx`
- Modify: `Fuexam-admin/app/source/catalog/page.tsx`
- Modify: `Fuexam-admin/app/source/purchases/page.tsx`
- Modify: any other `Fuexam-admin/app/**` or `Fuexam-admin/components/**` files with visible old-brand literals
- Test: `Fuexam-admin/package.json`

**Interfaces:**
- Consumes: Task 1 inventory buckets `admin-ui` and `metadata`
- Produces: admin-visible titles/descriptions/labels use `Fuexam` and `Fuexam Point`

- [ ] **Step 1: Write a focused failing check for admin visible brand literals**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" \
  Fuexam-admin/app/layout.tsx \
  Fuexam-admin/app/source/catalog/page.tsx \
  Fuexam-admin/app/source/purchases/page.tsx \
  Fuexam-admin/components
```

Expected: current old-brand hits are printed.

- [ ] **Step 2: Update admin metadata in `Fuexam-admin/app/layout.tsx`**

```ts
export const metadata: Metadata = {
  title: "Fuexam Admin",
  description: "Bảng điều khiển quản trị Fuexam",
};
```

Expected: browser tab/admin metadata branding matches `Fuexam`.

- [ ] **Step 3: Update visible point-brand/admin copy in Source pages**

```ts
// Fuexam-admin/app/source/catalog/page.tsx
<AdminShell
  title="Source — Tài liệu"
  description="Quản lý mã môn, giá Fuexam Point và thời hạn truy cập"
/>
<Label htmlFor="price">Giá (Fuexam Point)</Label>

// Fuexam-admin/app/source/purchases/page.tsx
<AdminShell title="Source — Đơn mua" description="Theo dõi giao dịch và hoàn tiền Fuexam Point">
```

Expected: admin-facing Source pages use `Fuexam Point` consistently in descriptions and labels.

- [ ] **Step 4: Search for remaining admin visible brand literals and resolve only literal-copy hits**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam-admin
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" app components
```

Expected: any remaining hits are reviewed; internal role identifiers like `FUO_...` stay if non-visible.

- [ ] **Step 5: Run the smallest admin frontend validation**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam-admin
cat package.json
```

Then run the smallest available validation command, preferring one of:

```bash
pnpm lint
# or
pnpm typecheck
# or
pnpm build
```

Expected: command exits 0.

- [ ] **Step 6: Commit**

```bash
git add Fuexam-admin/app/layout.tsx Fuexam-admin/app/source/catalog/page.tsx Fuexam-admin/app/source/purchases/page.tsx Fuexam-admin/components
git commit -m "feat: update admin visible branding"
```

Expected: one commit covering admin visible branding changes only.

---

### Task 5: Update backend mail/template/notification copy if any visible old-brand text exists

**Files:**
- Modify: exact backend files discovered by search, only if they contain visible user copy
- Test: `backend/notification` or other impacted module build/test command

**Interfaces:**
- Consumes: Task 1 inventory buckets `mail` and `user-message`
- Produces: backend-generated visible text uses `Fuexam`/`Fuexam Point`; technical identifiers remain unchanged

- [ ] **Step 1: Confirm whether any backend-visible old-brand strings actually exist**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/backend
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" . \
  --glob '!**/target/**' \
  --glob '*/src/main/resources/**' \
  --glob '*/src/main/java/**'
```

Expected: either zero visible-copy hits or a short list of exact files with user-facing strings.

- [ ] **Step 2: If there are no backend-visible hits, record skip and do not edit backend files**

```text
No backend mail/template/notification copy contained visible old-brand text.
Skip Task 5 code edits.
```

Expected: backend remains untouched.

- [ ] **Step 3: If visible backend hits exist, edit only user-facing literals**

```text
Allowed examples:
- email subject "FuOverflow" -> "Fuexam"
- notification/body string "FUO Point" -> "Fuexam Point"

Forbidden examples:
- package com.fuoverflow...
- <artifactId>fuoverflow-...</artifactId>
- Docker user/group names like fuoverflow
```

Expected: only visible backend copy changes.

- [ ] **Step 4: Validate only the impacted backend module if Task 5 made changes**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/backend
mvn -q -pl notification -am test
```

If a different module was edited, swap `notification` for that exact module.

Expected: exits 0.

- [ ] **Step 5: Commit only if backend files changed**

```bash
git add backend
git commit -m "feat: update backend visible branding copy"
```

Expected: no commit if Task 5 was a no-op; otherwise one backend-only commit.

---

### Task 6: Final verification and residue review

**Files:**
- Modify: none unless verification exposes missed visible-copy occurrences
- Test: repo-wide search + smallest useful builds already established

**Interfaces:**
- Consumes: Tasks 2-5 completed changes
- Produces: final proof that only non-visible/internal old-brand residues remain

- [ ] **Step 1: Run a repo-wide residue search**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone
rg -n -i "FuOverflow|FUO Point|FUO|Fuo" . \
  --glob '!**/target/**' \
  --glob '!**/.git/**' \
  --glob '!**/node_modules/**' \
  --glob '!**/.next/**' \
  --glob '!docs/superpowers/**'
```

Expected: remaining hits should be only intentional internal identifiers or known non-visible residues.

- [ ] **Step 2: Review every remaining hit and classify it explicitly**

```text
Acceptable leftovers:
- package names under com.fuoverflow
- Maven artifact names
- CSS token names like --color-fuo-600
- internal role codes such as FUO_* if not rendered

Unacceptable leftovers:
- browser titles
- labels/descriptions/buttons
- email subjects/bodies
- admin/user visible messages
```

Expected: zero unacceptable leftovers.

- [ ] **Step 3: Re-run the chosen validation commands for changed apps/modules**

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam && pnpm lint
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/Fuexam-admin && pnpm lint
```

If lint is unavailable, use the alternative validation command established earlier. If backend files changed, also run:

```bash
cd /home/thachnn/Desktop/github/Fuovfolder-Clone/backend && mvn -q -pl <edited-module> -am test
```

Expected: all executed validation commands exit 0.

- [ ] **Step 4: Capture final status for reporting**

```bash
git status --short
```

Expected: only the intended modified files are listed before any final integration step.

- [ ] **Step 5: Commit any last verification-fix edits if needed**

```bash
git add -A
git commit -m "chore: finalize Fuexam visible branding sweep"
```

Expected: use only if verification uncovered and fixed missed visible-copy strings; otherwise skip this commit.

---

## Self-Review

### Spec coverage
- Visible main frontend UI covered by Tasks 2-3.
- Visible admin UI covered by Task 4.
- Visible metadata/browser/platform branding covered by Tasks 2 and 4.
- Backend mail/template/notification/user-message surfaces covered by Task 5.
- Verification/reporting of intentional internal leftovers covered by Task 6.
- Constraint to avoid internal identifier renames repeated in every relevant task.

### Placeholder scan
- No `TBD`, `TODO`, or "implement later" placeholders remain.
- Conditional backend task is explicit: skip if no visible hits exist; otherwise edit exact discovered files.
- Validation commands are concrete, with a documented fallback if package scripts differ.

### Type consistency
- No new runtime interfaces are introduced.
- Metadata examples use existing `Metadata` exports already present in the repo.
- Copy-only tasks preserve existing component signatures and props.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-22-fuexam-visible-branding-replacement.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
