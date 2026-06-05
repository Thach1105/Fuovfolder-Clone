"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { PostList } from "@/components/forum/PostList";
import { ReplyForm } from "@/components/forum/ReplyForm";
import { ThreadWatchButton } from "@/components/forum/ThreadWatchButton";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import { threadTypeLabel } from "@/lib/forum-thread-types";
import {
  type Post,
  type ThreadDetail,
  authorInitial,
  getThread,
  listThreadPosts,
} from "@/lib/api/forum";
import { formatDateTime } from "@/lib/format-datetime";

const PAGE_SIZE = 20;

export default function ThreadDetailPage() {
  const params = useParams();
  const threadId = typeof params.threadId === "string" ? params.threadId : "";

  const [thread, setThread] = useState<ThreadDetail | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [postsLoading, setPostsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { user } = useAuth();

  useEffect(() => {
    if (!threadId) return;
    setLoading(true);
    getThread(threadId)
      .then(setThread)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Không tải được chủ đề"),
      )
      .finally(() => setLoading(false));
  }, [threadId]);

  const loadPosts = useCallback(async () => {
    if (!threadId) return;
    setPostsLoading(true);
    try {
      const result = await listThreadPosts(threadId, page, PAGE_SIZE);
      setPosts(result.items);
      setTotalPages(result.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được bài viết");
    } finally {
      setPostsLoading(false);
    }
  }, [threadId, page]);

  useEffect(() => {
    loadPosts();
  }, [loadPosts]);

  if (loading) {
    return <p className="text-sm text-slate-500">Đang tải chủ đề...</p>;
  }

  if (!thread) {
    return (
      <div className="card p-6 text-sm text-slate-600">
        <p>{error ?? "Không tìm thấy chủ đề."}</p>
        <Link href="/" className="mt-3 inline-block font-medium text-fuo-600 hover:underline">
          ← Về trang chủ
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <PromoBanner />

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <Link href="/" className="text-sm font-medium text-fuo-600 hover:underline">
            ← Diễn đàn
          </Link>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span className="rounded bg-fuo-50 px-2 py-0.5 text-xs font-semibold text-fuo-700">
              {threadTypeLabel(thread.threadType)}
            </span>
            {thread.materialType && (
              <span className="rounded bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800">
                {thread.materialType}
              </span>
            )}
          </div>
          <h1 className="mt-2 text-2xl font-bold text-slate-900">{thread.title}</h1>
          <div className="mt-2 flex flex-wrap gap-3 text-sm text-slate-500">
            <span className="flex items-center gap-1.5">
              <span className="flex h-7 w-7 items-center justify-center rounded-full bg-fuo-50 text-xs font-bold text-fuo-700">
                {authorInitial(thread.authorHandle)}
              </span>
              {thread.authorHandle ?? "Ẩn danh"}
            </span>
            <span>{thread.replyCount} trả lời</span>
            <span>{thread.viewCount.toLocaleString("vi-VN")} lượt xem</span>
            <span>Cập nhật {formatDateTime(thread.lastPostAt)}</span>
          </div>
        </div>
        <div className="flex flex-col items-end gap-2">
          {user && <ThreadWatchButton threadId={threadId} />}
          {thread.sourceUrl && (
            <a
              href={thread.sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="btn-secondary text-xs"
            >
              Mở trên fuoverflow.com
            </a>
          )}
        </div>
      </div>

      <section className="card overflow-hidden">
        <div className="border-b border-slate-100 px-4 py-3">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
            Bài viết ({thread.replyCount + 1})
          </h2>
        </div>
        {postsLoading ? (
          <p className="px-4 py-8 text-sm text-slate-500">Đang tải bài viết...</p>
        ) : (
          <PostList posts={posts} />
        )}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-3 border-t border-slate-100 py-3">
            <button
              type="button"
              className="btn-secondary"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
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
              onClick={() => setPage((p) => p + 1)}
            >
              Sau →
            </button>
          </div>
        )}
        {user && <ReplyForm threadId={threadId} onPosted={loadPosts} />}
      </section>
    </div>
  );
}
