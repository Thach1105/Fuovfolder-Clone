"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ThreadTable } from "@/components/forum/ThreadTable";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import {
  type ThreadSummary,
  listForums,
  listWatchedThreads,
} from "@/lib/api/forum";

const PAGE_SIZE = 20;

export default function WatchedThreadsPage() {
  const { user, loading: authLoading } = useAuth();
  const [threads, setThreads] = useState<ThreadSummary[]>([]);
  const [forumTitleById, setForumTitleById] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listForums()
      .then((forums) => setForumTitleById(Object.fromEntries(forums.map((f) => [f.id, f.title]))))
      .catch(() => setForumTitleById({}));
  }, []);

  const load = useCallback(async () => {
    if (!user) return;
    setLoading(true);
    setError(null);
    try {
      const result = await listWatchedThreads(page, PAGE_SIZE);
      setThreads(result.items);
      setTotalPages(result.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được chủ đề đang theo dõi");
      setThreads([]);
    } finally {
      setLoading(false);
    }
  }, [user, page]);

  useEffect(() => {
    load();
  }, [load]);

  if (authLoading) {
    return <p className="text-sm text-slate-500">Đang kiểm tra đăng nhập...</p>;
  }

  if (!user) {
    return (
      <div className="card p-6 text-sm text-slate-600">
        <p>Bạn cần đăng nhập để xem chủ đề đang theo dõi.</p>
        <Link href="/login" className="mt-3 inline-block font-medium text-fuo-600 hover:underline">
          Đăng nhập
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <PromoBanner />
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Đã theo dõi</h1>
        <p className="mt-1 text-sm text-slate-500">
          Các chủ đề bạn đã chọn theo dõi để nhận cập nhật sau này.
        </p>
      </div>

      <div className="card overflow-hidden">
        {error && (
          <p className="border-b border-red-100 bg-red-50 px-4 py-2 text-sm text-red-800">
            {error}
          </p>
        )}
        {loading ? (
          <p className="px-4 py-8 text-sm text-slate-500">Đang tải...</p>
        ) : (
          <ThreadTable
            threads={threads}
            forumTitleById={forumTitleById}
            emptyMessage="Bạn chưa theo dõi chủ đề nào. Bật “Theo dõi tin đăng này” khi đăng bài hoặc nhấn Theo dõi trên trang chủ đề."
          />
        )}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-3 border-t border-slate-100 py-3">
            <button
              type="button"
              className="btn-secondary"
              disabled={page === 0}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              ← Trước
            </button>
            <span className="text-sm text-slate-600">
              Trang {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              className="btn-secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((value) => value + 1)}
            >
              Sau →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
