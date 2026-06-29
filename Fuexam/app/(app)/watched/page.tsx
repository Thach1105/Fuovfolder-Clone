"use client";

import { useCallback, useEffect, useState } from "react";
import { ThreadTable } from "@/components/forum/ThreadTable";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { AuthGuard } from "@/components/shared/auth-guard";
import { PaginationBar } from "@/components/shared/pagination-bar";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useAsyncAction } from "@/hooks/use-async-action";
import { usePagination } from "@/hooks/use-pagination";
import {
  type ThreadSummary,
  listForums,
  listWatchedThreads,
} from "@/lib/api/forum";

import { DEFAULT_PAGE_SIZE } from "@/lib/constants/pagination";

export default function WatchedThreadsPage() {
  const { user } = useAuth();
  const [threads, setThreads] = useState<ThreadSummary[]>([]);
  const [forumTitleById, setForumTitleById] = useState<Record<string, string>>({});
  const pagination = usePagination();
  const { loading, error, run } = useAsyncAction("Không tải được chủ đề đang theo dõi");

  useEffect(() => {
    listForums()
      .then((forums) => setForumTitleById(Object.fromEntries(forums.map((f) => [f.id, f.title]))))
      .catch(() => setForumTitleById({}));
  }, []);

  const load = useCallback(() => {
    if (!user) return;
    setThreads([]);
    run(async () => {
      const result = await listWatchedThreads(pagination.page, DEFAULT_PAGE_SIZE);
      setThreads(result.items);
      pagination.updateFromResponse(result);
    });
  }, [user, pagination.page, run]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <AuthGuard>
    <div className="space-y-5">
      <PromoBanner />
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Đã theo dõi</h1>
        <p className="mt-1 text-sm text-slate-500">
          Các chủ đề bạn đã chọn theo dõi để nhận cập nhật sau này.
        </p>
      </div>

      <div className="card overflow-hidden">
        <ErrorBanner message={error} />
        {loading ? (
          <LoadingState className="px-4 py-8" />
        ) : (
          <ThreadTable
            threads={threads}
            forumTitleById={forumTitleById}
            emptyMessage="Bạn chưa theo dõi chủ đề nào. Bật “Theo dõi tin đăng này” khi đăng bài hoặc nhấn Theo dõi trên trang chủ đề."
          />
        )}
        <PaginationBar
          page={pagination.page}
          totalPages={pagination.totalPages}
          onPageChange={pagination.setPage}
        />
      </div>
    </div>
    </AuthGuard>
  );
}
