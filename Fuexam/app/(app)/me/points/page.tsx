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
            : "Không tải được dữ liệu Fuexam Point.",
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
            : "Không tải được lịch sử Fuexam Point.",
        );
        setPage(ledger.page);
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
        <h1 className="font-display text-4xl tracking-tight">Fuexam Point của tôi</h1>
        <p className="mt-2 text-sm text-muted-foreground">Đang tải...</p>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <div className="mb-8 text-center">
        <h1 className="font-display text-4xl tracking-tight">Fuexam Point của tôi</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Theo dõi số dư hiện tại và lịch sử giao dịch Fuexam Point của bạn.
        </p>
      </div>

      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-center text-sm text-destructive">
          {error}
        </div>
      )}

      <section className="mb-10 rounded-2xl border border-foreground/10 bg-background p-6 shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          Số dư hiện tại
        </p>
        {initialLoading || balance === null ? (
          <p className="mt-2 text-4xl font-bold text-muted-foreground">—</p>
        ) : (
          <p className="mt-2 text-4xl font-bold text-emerald-700">
            {formatPoints(balance)}
          </p>
        )}

        <div className="mt-6 flex flex-wrap gap-3">
          <Link
            href="/deposit"
            className="inline-flex items-center justify-center rounded-lg bg-emerald-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-emerald-500"
          >
            Nạp thêm Fuexam
          </Link>
        </div>
      </section>

      <section>
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">Lịch sử giao dịch</h2>
          {pageLoading && (
            <span className="text-xs text-muted-foreground">Đang tải trang...</span>
          )}
        </div>

        {initialLoading ? (
          <p className="text-sm text-muted-foreground">Đang tải lịch sử...</p>
        ) : entries.length === 0 ? (
          <div className="rounded-lg border border-foreground/10 bg-background p-8 text-center text-sm text-muted-foreground">
            Chưa có giao dịch Fuexam Point nào.
          </div>
        ) : (
          <>
            <ul className="divide-y divide-foreground/10 overflow-hidden rounded-2xl border border-foreground/10 bg-background shadow-sm">
              {entries.map((entry) => (
                <li
                  key={entry.id}
                  className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium">
                      {entry.reason?.trim() ? entry.reason : "Không rõ"}
                    </p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {entry.sourceType || "unknown"}
                      {entry.sourceId ? ` · ${entry.sourceId}` : ""}
                    </p>
                  </div>

                  <div className="flex flex-col items-start gap-1 sm:items-end">
                    <span
                      className={
                        entry.delta > 0
                          ? "text-sm font-semibold text-emerald-700"
                          : entry.delta < 0
                            ? "text-sm font-semibold text-destructive"
                            : "text-sm font-semibold text-muted-foreground"
                      }
                    >
                      {entry.delta > 0 ? "+" : ""}
                      {entry.delta.toLocaleString("vi-VN")} Fuexam
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {formatDateTime(entry.createdAt)}
                    </span>
                  </div>
                </li>
              ))}
            </ul>
            <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
              <button
                type="button"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={pageLoading || page === 0}
                className="rounded-lg border border-foreground/15 px-3 py-1.5 transition hover:border-foreground/40 disabled:cursor-not-allowed disabled:opacity-50"
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
                className="rounded-lg border border-foreground/15 px-3 py-1.5 transition hover:border-foreground/40 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Sau
              </button>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
