"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { MOCK_CONTRIBUTORS } from "@/data/mock-threads";
import { ThreadTable } from "@/components/forum/ThreadTable";
import { ThreadTabs } from "@/components/forum/ThreadTabs";
import { TopContributors } from "@/components/forum/TopContributors";
import { BackendStatus } from "@/components/layout/BackendStatus";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import {
  type Forum,
  type ThreadSummary,
  type ThreadTab,
  applyThreadTab,
  browseThreads,
  listForums,
} from "@/lib/api/forum";

export default function HomePage() {
  const [tab, setTab] = useState<ThreadTab>("discussion");
  const [threads, setThreads] = useState<ThreadSummary[]>([]);
  const [forums, setForums] = useState<Forum[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [forumList, threadPage] = await Promise.all([
        listForums(),
        browseThreads({ page: 0, size: 30 }),
      ]);
      setForums(forumList);
      setThreads(threadPage.items);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được diễn đàn");
      setThreads([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const forumTitleById = Object.fromEntries(forums.map((f) => [f.id, f.title]));
  const visibleThreads = applyThreadTab(threads, tab);

  return (
    <div className="space-y-4">
      <BackendStatus />
      <PromoBanner />

      <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
        <section className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h1 className="text-xl font-bold text-slate-900">FuOverflow Community</h1>
              {user && (
                <p className="text-sm text-slate-500">Xin chào, {user.displayName}</p>
              )}
            </div>
            <Link href="/forums/tai-lieu" className="btn-primary">
              Đăng tài liệu
            </Link>
          </div>

          <div className="flex flex-wrap gap-2">
            <Link href="/whats-new" className="btn-secondary text-xs">
              Bài mới
            </Link>
            <Link href="/forums" className="btn-secondary text-xs">
              Danh sách diễn đàn
            </Link>
            <Link href="/search" className="btn-secondary text-xs">
              Tìm trong diễn đàn
            </Link>
            <Link href="/awards" className="btn-secondary text-xs">
              Danh hiệu
            </Link>
          </div>

          {forums.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {forums.map((forum) => (
                <Link
                  key={forum.id}
                  href={`/forums/${forum.slug}`}
                  className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-medium text-slate-700 transition hover:border-fuo-300 hover:text-fuo-700"
                >
                  {forum.title}
                </Link>
              ))}
            </div>
          )}

          <div className="card overflow-hidden">
            <ThreadTabs active={tab} onChange={setTab} />
            {error && (
              <p className="border-b border-red-100 bg-red-50 px-4 py-2 text-sm text-red-800">
                {error}
              </p>
            )}
            {loading ? (
              <p className="px-4 py-8 text-sm text-slate-500">Đang tải chủ đề...</p>
            ) : (
              <ThreadTable
                threads={visibleThreads}
                forumTitleById={forumTitleById}
                emptyMessage={
                  tab === "confession"
                    ? "Chưa có chủ đề confession nào."
                    : "Chưa có chủ đề nào."
                }
              />
            )}
            <div className="border-t border-slate-100 px-4 py-3">
              <Link href="/forums" className="text-sm font-medium text-fuo-600 hover:underline">
                Xem tất cả diễn đàn →
              </Link>
            </div>
          </div>

          <section className="card p-4">
            <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-500">
              Khu vực điều hành
            </h2>
            <div className="grid gap-3 sm:grid-cols-3">
              {["Thông báo từ BQT", "Reports", "Thành viên vi phạm"].map((title) => (
                <div
                  key={title}
                  className="rounded-lg border border-slate-100 bg-slate-50 p-3 text-sm"
                >
                  <p className="font-medium text-slate-800">{title}</p>
                  <p className="mt-1 text-xs text-slate-500">0 chủ đề · 0 bài</p>
                </div>
              ))}
            </div>
          </section>
        </section>

        <aside className="space-y-4">
          <TopContributors contributors={MOCK_CONTRIBUTORS} />
        </aside>
      </div>
    </div>
  );
}
