# `/me/points` Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real `/me/points` page to the Fuexam Next.js app that fixes the current 404 hit from the membership CTA and surfaces the user's FUO Point balance and ledger.

**Architecture:** Single client component under the existing `(app)` route group, reusing the existing `getPointsBalance()` and `getPointsLedger(page, size)` API clients from `Fuexam/lib/api/points.ts`. No backend or API client changes. The `(app)/layout.tsx` already supplies header/backdrop/main wrapper, so the new page just provides its inner content.

**Tech Stack:** Next.js App Router, React 19, TypeScript, Tailwind, existing internal `lib/api/*` and `lib/format-*` helpers, `useAuth` from `lib/auth/AuthProvider`.

## Global Constraints

- File path: `Fuexam/app/(app)/me/points/page.tsx`
- Use `"use client";` directive at the top of the file (page is interactive).
- Use only existing API client functions — do not change `Fuexam/lib/api/points.ts`.
- Reuse existing formatters: `formatPoints` from `Fuexam/lib/format-points.ts`, `formatDateTime` from `Fuexam/lib/format-datetime.ts`.
- Use `useAuth()` from `Fuexam/lib/auth/AuthProvider` for the current user and `loading` flag.
- Use `ApiError` from `Fuexam/lib/api/client` for error messages.
- Use the existing visual language: slate/emerald palette, rounded-2xl cards, `mx-auto max-w-5xl px-4 py-10` outer container (matches `Fuexam/app/(app)/deposit/page.tsx`).
- Vietnamese copy for user-facing strings.
- No new dependencies, no new tests under the app (the app currently has no test framework configured — verification is manual smoke testing only).
- Frequent commits. One commit per task.

---

## File Structure

Files created or modified by this plan:

- **Create** `Fuexam/app/(app)/me/points/page.tsx` — the new client page. Single responsibility: render the user's FUO Point balance and ledger for the route `/me/points`.

Files inspected but **not modified**:

- `Fuexam/lib/api/points.ts` — source of `getPointsBalance`, `getPointsLedger`, and shared types.
- `Fuexam/lib/api/client.ts` — source of `ApiError`.
- `Fuexam/lib/auth/AuthProvider.tsx` — source of `useAuth`.
- `Fuexam/lib/format-points.ts` — source of `formatPoints`.
- `Fuexam/lib/format-datetime.ts` — source of `formatDateTime`.
- `Fuexam/app/(app)/layout.tsx` — wraps all `(app)` pages; we do not need to touch it.
- `Fuexam/app/(app)/membership/page.tsx` — already links to `/me/points`; no change required.

---

## Task 1: Create the `/me/points` page

**Files:**
- Create: `Fuexam/app/(app)/me/points/page.tsx`
- Inspect: `Fuexam/lib/api/points.ts`, `Fuexam/lib/api/client.ts`, `Fuexam/lib/auth/AuthProvider.tsx`, `Fuexam/lib/format-points.ts`, `Fuexam/lib/format-datetime.ts`

**Interfaces:**
- Consumes:
  - `useAuth(): { user: User | null; loading: boolean }` from `@/lib/auth/AuthProvider`
  - `ApiError` from `@/lib/api/client`
  - `getPointsBalance(): Promise<PointsBalance>` from `@/lib/api/points`
  - `getPointsLedger(page: number, size: number): Promise<PointsLedgerPage>` from `@/lib/api/points`
  - `formatPoints(n: number): string` from `@/lib/format-points`
  - `formatDateTime(iso: string | null | undefined): string` from `@/lib/format-datetime`
  - `useRouter()` from `next/navigation` for auth redirect
- Produces: default-exported `PointsPage` component mounted at route `/me/points`.

- [ ] **Step 1: Scaffold file with imports and a placeholder render**

Create `Fuexam/app/(app)/me/points/page.tsx` with the following content:

```tsx
"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import {
  getPointsBalance,
  getPointsLedger,
  type PointsLedgerEntry,
  type PointsLedgerPage,
} from "@/lib/api/points";
import { formatDateTime } from "@/lib/format-datetime";
import { formatPoints } from "@/lib/format-points";

const PAGE_SIZE = 20;

export default function PointsPage() {
  const router = useRouter();
  const { user, loading: authLoading } = useAuth();

  const [balance, setBalance] = useState<number | null>(null);
  const [ledger, setLedger] = useState<PointsLedgerPage | null>(null);
  const [page, setPage] = useState(0);
  const [initialLoading, setInitialLoading] = useState(true);
  const [pageLoading, setPageLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authLoading && !user) {
      router.replace(`/login?next=${encodeURIComponent("/me/points")}`);
    }
  }, [authLoading, user, router]);

  useEffect(() => {
    if (!user) return;

    let cancelled = false;
    setInitialLoading(true);
    setError(null);

    Promise.all([getPointsBalance(), getPointsLedger(0, PAGE_SIZE)])
      .then(([balanceResponse, ledgerResponse]) => {
        if (cancelled) return;
        setBalance(balanceResponse.balance);
        setLedger(ledgerResponse);
        setPage(ledgerResponse.page);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(
          err instanceof ApiError
            ? err.message
            : "Không tải được dữ liệu FUO Point.",
        );
      })
      .finally(() => {
        if (!cancelled) setInitialLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [user]);

  useEffect(() => {
    if (!user || initialLoading || !ledger || page === ledger.page) return;

    let cancelled = false;
    setPageLoading(true);
    setError(null);

    getPointsLedger(page, PAGE_SIZE)
      .then((ledgerResponse) => {
        if (cancelled) return;
        setLedger(ledgerResponse);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(
          err instanceof ApiError
            ? err.message
            : "Không tải được lịch sử FUO Point.",
        );
      })
      .finally(() => {
        if (!cancelled) setPageLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [page, user, initialLoading, ledger]);

  const entries = useMemo<PointsLedgerEntry[]>(() => ledger?.items ?? [], [ledger]);

  if (authLoading || (!user && !error)) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-10">
        <h1 className="text-3xl font-bold text-slate-100">FUO Point của tôi</h1>
        <p className="mt-2 text-sm text-slate-400">Đang tải...</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <div className="mb-8 text-center">
        <h1 className="text-3xl font-bold text-slate-100">FUO Point của tôi</h1>
        <p className="mt-2 text-sm text-slate-400">
          Theo dõi số dư hiện tại và lịch sử giao dịch FUO Point của bạn.
        </p>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Run TypeScript check for the scaffold**

Run:

```bash
cd Fuexam
pnpm exec tsc --noEmit
```

Expected: command exits with code 0.

- [ ] **Step 3: Add the balance card and shared error banner**

Inside the returned wrapper `<div className="mx-auto max-w-5xl px-4 py-10">`, immediately after the existing header block, insert:

```tsx
      {error && (
        <div className="mb-6 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-center text-sm text-red-300">
          {error}
        </div>
      )}

      <section className="mb-10 rounded-2xl border border-slate-800 bg-slate-900/50 p-6">
        <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Số dư hiện tại
        </p>
        {initialLoading || balance === null ? (
          <p className="mt-2 text-4xl font-bold text-slate-500">—</p>
        ) : (
          <p className="mt-2 text-4xl font-bold text-emerald-400">
            {formatPoints(balance)}
          </p>
        )}

        <div className="mt-6 flex flex-wrap gap-3">
          <Link
            href="/deposit"
            className="inline-flex items-center justify-center rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-emerald-500"
          >
            Nạp thêm FUO
          </Link>
        </div>
      </section>
```

- [ ] **Step 4: Add the ledger list and empty/loading states**

Still inside the same returned wrapper, append this section after the balance card:

```tsx
      <section>
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold text-slate-100">Lịch sử giao dịch</h2>
          {pageLoading && (
            <span className="text-xs text-slate-500">Đang tải trang...</span>
          )}
        </div>

        {initialLoading ? (
          <p className="text-sm text-slate-400">Đang tải lịch sử...</p>
        ) : entries.length === 0 ? (
          <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-8 text-center text-sm text-slate-400">
            Chưa có giao dịch FUO Point nào.
          </div>
        ) : (
          <ul className="divide-y divide-slate-800 overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/50">
            {entries.map((entry) => (
              <li
                key={entry.id}
                className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-slate-100">
                    {entry.reason?.trim() ? entry.reason : "Không rõ"}
                  </p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {entry.sourceType || "unknown"}
                    {entry.sourceId ? ` · ${entry.sourceId}` : ""}
                  </p>
                </div>

                <div className="flex flex-col items-start gap-1 sm:items-end">
                  <span
                    className={
                      entry.delta > 0
                        ? "text-sm font-semibold text-emerald-400"
                        : entry.delta < 0
                          ? "text-sm font-semibold text-red-400"
                          : "text-sm font-semibold text-slate-300"
                    }
                  >
                    {entry.delta > 0 ? "+" : ""}
                    {entry.delta.toLocaleString("vi-VN")} FUO
                  </span>
                  <span className="text-xs text-slate-500">
                    {formatDateTime(entry.createdAt)}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
```

- [ ] **Step 5: Add pagination controls below the ledger list**

Immediately after the closing `</ul>` block inside the `entries.length !== 0` branch, append:

```tsx
          <div className="mt-4 flex items-center justify-between text-sm text-slate-400">
            <button
              type="button"
              onClick={() => setPage((current) => Math.max(0, current - 1))}
              disabled={pageLoading || page === 0}
              className="rounded-lg border border-slate-800 px-3 py-1.5 transition hover:border-slate-600 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Trước
            </button>

            <span>
              Trang {page + 1}
              {ledger ? ` / ${Math.max(1, ledger.totalPages)}` : ""}
            </span>

            <button
              type="button"
              onClick={() =>
                setPage((current) =>
                  ledger && current + 1 < ledger.totalPages ? current + 1 : current,
                )
              }
              disabled={pageLoading || !ledger || page + 1 >= ledger.totalPages}
              className="rounded-lg border border-slate-800 px-3 py-1.5 transition hover:border-slate-600 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Sau
            </button>
          </div>
```

- [ ] **Step 6: Run TypeScript and lint checks**

Run:

```bash
cd Fuexam
pnpm exec tsc --noEmit
pnpm exec next lint --file app/\(app\)/me/points/page.tsx || pnpm exec next lint
```

Expected:
- `tsc` exits 0.
- `next lint` reports no new issues from `app/(app)/me/points/page.tsx`.

- [ ] **Step 7: Commit Task 1**

```bash
git add Fuexam/app/\(app\)/me/points/page.tsx
git commit -m "feat(fuexam): add /me/points page

Create a real points page for authenticated users using the existing
balance and ledger APIs. The page shows the FUO balance, paginated
history, and a CTA into the existing deposit flow.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: Manual smoke verification

**Files:** none modified.

**Interfaces:**
- Consumes: local frontend app, running backend with auth + points data.
- Produces: manual verification evidence for route behavior, balance render, ledger render, empty state, pagination, and deposit CTA.

- [ ] **Step 1: Start the frontend app**

Run:

```bash
cd Fuexam
pnpm dev
```

Expected: dev server boots successfully; no compile errors mentioning `me/points`.

- [ ] **Step 2: Verify unauthenticated redirect**

Open `http://localhost:3000/me/points` in a logged-out browser session.

Expected: redirect to `/login?next=%2Fme%2Fpoints`.

- [ ] **Step 3: Verify authenticated balance + ledger render**

Log in as a user with at least one points ledger entry, then open `/me/points`.

Expected:
- title `FUO Point của tôi` visible,
- balance card visible,
- deposit CTA `Nạp thêm FUO` visible and links to `/deposit`,
- ledger rows visible,
- positive deltas green, negative deltas red,
- timestamps formatted in `vi-VN` style,
- page indicator visible.

- [ ] **Step 4: Verify empty state**

Log in as a user with zero ledger rows and open `/me/points`.

Expected:
- balance card still renders,
- ledger section shows `Chưa có giao dịch FUO Point nào.`,
- pagination controls do not render.

- [ ] **Step 5: Verify pagination transitions**

Using a user whose ledger spans more than one page, click `Sau` then `Trước`.

Expected:
- ledger rows update,
- page indicator updates,
- `Trước` disables on first page,
- `Sau` disables on last page,
- temporary `Đang tải trang...` hint appears during fetch.

- [ ] **Step 6: Stop the dev server**

Stop with `Ctrl+C`.

Expected: process exits cleanly.

---

## Self-Review

- **Spec coverage:**
  - `/me/points` route creation → Task 1.
  - authenticated access + login redirect → Task 1 Step 1.
  - balance card + deposit CTA → Task 1 Step 3.
  - ledger list + delta color + reason/source/timestamp → Task 1 Step 4.
  - pagination → Task 1 Step 5.
  - loading/empty/error states → Task 1 Steps 1, 3, 4, 5.
  - manual smoke verification → Task 2.
- **Placeholder scan:** no TBD/TODO/placeholders.
- **Type consistency:** all types/functions sourced from existing imports in `Fuexam/lib/api/points.ts`, `Fuexam/lib/auth/AuthProvider.tsx`, `Fuexam/lib/api/client.ts`, `Fuexam/lib/format-datetime.ts`, `Fuexam/lib/format-points.ts`.
