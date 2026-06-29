"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ThreadTable } from "@/components/forum/ThreadTable";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import { WHATS_NEW_PAGE_SIZE } from "@/lib/constants/pagination";
import { type Forum, browseThreads, listForums } from "@/lib/api/forum";

export default function WhatsNewPage() {
  const [forums, setForums] = useState<Forum[]>([]);
  const { loading, error, run } = useAsyncAction("Không tải được bài mới");

  const forumTitleById = Object.fromEntries(forums.map((f) => [f.id, f.title]));
  const [threadItems, setThreadItems] = useState<
    Awaited<ReturnType<typeof browseThreads>>["items"]
  >([]);

  useEffect(() => {
    run(async () => {
      const [forumList, page] = await Promise.all([
        listForums(),
        browseThreads({ page: 0, size: WHATS_NEW_PAGE_SIZE }),
      ]);
      setForums(forumList);
      setThreadItems(page.items);
    });
  }, [run]);

  return (
    <div className="space-y-5">
      <PromoBanner />

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Bài mới</h1>
          <p className="text-sm text-slate-600">
            Chủ đề được cập nhật gần đây nhất.
          </p>
        </div>
        <Link href="/" className="text-sm font-medium text-fuo-600 hover:underline">
          ← Trang chủ
        </Link>
      </div>

      <div className="card overflow-hidden">
        <ErrorBanner message={error} />
        {loading ? (
          <LoadingState className="px-4 py-8" />
        ) : (
          <ThreadTable
            threads={threadItems}
            forumTitleById={forumTitleById}
            emptyMessage="Chưa có hoạt động mới."
          />
        )}
      </div>
    </div>
  );
}
