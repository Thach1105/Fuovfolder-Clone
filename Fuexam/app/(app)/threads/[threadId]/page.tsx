"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { PostList } from "@/components/forum/PostList";
import { ReplyForm } from "@/components/forum/ReplyForm";
import { ThreadWatchButton } from "@/components/forum/ThreadWatchButton";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { LoadingState } from "@/components/ui/loading-state";
import { PaginationBar } from "@/components/shared/pagination-bar";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import { useAsyncAction } from "@/hooks/use-async-action";
import { usePagination } from "@/hooks/use-pagination";
import { threadTypeLabel } from "@/lib/forum-thread-types";
import {
  type Post,
  type ThreadDetail,
  getThread,
  listThreadPosts,
} from "@/lib/api/forum";
import { getInitial } from "@/lib/utils/text";
import { ReportContentDialog } from "@/components/forum/ReportContentDialog";
import { formatDateTime } from "@/lib/format-datetime";

import { DEFAULT_PAGE_SIZE } from "@/lib/constants/pagination";

export default function ThreadDetailPage() {
  const params = useParams();
  const threadId = typeof params.threadId === "string" ? params.threadId : "";

  const [thread, setThread] = useState<ThreadDetail | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const pagination = usePagination();
  const { loading, error, run } = useAsyncAction("Không tải được chủ đề");
  const { loading: postsLoading, error: postsError, run: runPosts } = useAsyncAction("Không tải được bài viết");
  const { user } = useAuth();
  const canReply = can(user, "forum.post:create");

  useEffect(() => {
    if (!threadId) return;
    run(async () => {
      setThread(await getThread(threadId));
    });
  }, [threadId, run]);

  const loadPosts = useCallback(() => {
    if (!threadId) return;
    runPosts(async () => {
      const result = await listThreadPosts(threadId, pagination.page, DEFAULT_PAGE_SIZE);
      setPosts(result.items);
      pagination.updateFromResponse(result);
    });
  }, [threadId, pagination.page, runPosts]);

  useEffect(() => {
    loadPosts();
  }, [loadPosts]);

  if (loading) {
    return <LoadingState message="Đang tải chủ đề..." />;
  }

  if (!thread) {
    return (
      <div className="card p-6 text-sm text-slate-600">
        <p>{error ?? postsError ?? "Không tìm thấy chủ đề."}</p>
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
                {getInitial(thread.authorHandle)}
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
          <ReportContentDialog targetType="thread" targetId={threadId} />
          {thread.sourceUrl && (
            <a
              href={thread.sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="btn-secondary text-xs"
            >
              Mở trên Fuexam.com
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
          <LoadingState message="Đang tải bài viết..." className="px-4 py-8" />
        ) : (
          <PostList posts={posts} threadId={threadId} onChanged={loadPosts} />
        )}
        <PaginationBar
          page={pagination.page}
          totalPages={pagination.totalPages}
          onPageChange={pagination.setPage}
        />
        {canReply ? (
          <ReplyForm threadId={threadId} onPosted={loadPosts} />
        ) : user ? (
          <div className="border-t border-slate-100 px-4 py-4 text-sm text-slate-600">
            Trả lời yêu cầu gói{" "}
            <Link href="/membership" className="font-medium text-fuo-600 hover:underline">
              Fuexam Member
            </Link>
            .
          </div>
        ) : null}
      </section>
    </div>
  );
}
