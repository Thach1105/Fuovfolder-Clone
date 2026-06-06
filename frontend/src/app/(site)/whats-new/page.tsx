"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ThreadTable } from "@/components/forum/ThreadTable";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { ApiError } from "@/lib/api/client";
import { type Forum, browseThreads, listForums } from "@/lib/api/forum";

export default function WhatsNewPage() {
  const [forums, setForums] = useState<Forum[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const forumTitleById = Object.fromEntries(forums.map((f) => [f.id, f.title]));
  const [threadItems, setThreadItems] = useState<
    Awaited<ReturnType<typeof browseThreads>>["items"]
  >([]);

  useEffect(() => {
    Promise.all([listForums(), browseThreads({ page: 0, size: 40 })])
      .then(([forumList, page]) => {
        setForums(forumList);
        setThreadItems(page.items);
      })
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Không tải được bài mới"),
      )
      .finally(() => setLoading(false));
  }, []);

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
        {error && (
          <p className="border-b border-red-100 bg-red-50 px-4 py-2 text-sm text-red-800">
            {error}
          </p>
        )}
        {loading ? (
          <p className="px-4 py-8 text-sm text-slate-500">Đang tải...</p>
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
