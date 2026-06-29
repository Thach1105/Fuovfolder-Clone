"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import { ApiError } from "@/lib/api/client";
import type { Post } from "@/lib/api/forum";
import {
  approveModerationPost,
  listModerationQueue,
  rejectModerationPost,
} from "@/lib/api/moderation";
import { formatDateTime } from "@/lib/format-datetime";

export default function AdminModerationQueuePage() {
  const [items, setItems] = useState<Post[]>([]);
  const { loading, error: loadError, run } = useAsyncAction("Không tải được hàng chờ");
  const [actionError, setActionError] = useState<string | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);

  const error = loadError || actionError;

  const load = useCallback(() => run(async () => {
    const page = await listModerationQueue(0, 50);
    setItems(page.items);
  }), [run]);

  useEffect(() => {
    load();
  }, [load]);

  async function approve(postId: string) {
    setActingId(postId);
    setActionError(null);
    try {
      await approveModerationPost(postId);
      await load();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Không duyệt được bài");
    } finally {
      setActingId(null);
    }
  }

  async function reject(postId: string) {
    setActingId(postId);
    setActionError(null);
    try {
      await rejectModerationPost(postId);
      await load();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Không từ chối được bài");
    } finally {
      setActingId(null);
    }
  }

  return (
    <AdminShell title="Moderation — Hàng chờ duyệt" description="Duyệt bài viết từ thành viên chưa VIP">
      <ErrorBanner message={error} className="mb-4" />

      {loading ? (
        <LoadingState />
      ) : items.length === 0 ? (
        <p className="text-sm text-slate-400">Không có bài chờ duyệt.</p>
      ) : (
        <div className="space-y-4">
          {items.map((post) => (
            <article key={post.id} className="rounded-xl border border-slate-800 bg-slate-900/50 p-4">
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <div className="text-sm text-slate-300">
                  <span className="font-medium text-white">{post.authorHandle ?? "Thành viên"}</span>
                  <span className="mx-2 text-slate-600">·</span>
                  {formatDateTime(post.createdAt)}
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="btn-primary text-xs"
                    disabled={actingId === post.id}
                    onClick={() => approve(post.id)}
                  >
                    Duyệt
                  </button>
                  <button
                    type="button"
                    className="btn-secondary text-xs"
                    disabled={actingId === post.id}
                    onClick={() => reject(post.id)}
                  >
                    Từ chối
                  </button>
                </div>
              </div>
              <div
                className="prose prose-sm max-w-none prose-invert"
                dangerouslySetInnerHTML={{ __html: post.bodyHtml }}
              />
            </article>
          ))}
        </div>
      )}
    </AdminShell>
  );
}
