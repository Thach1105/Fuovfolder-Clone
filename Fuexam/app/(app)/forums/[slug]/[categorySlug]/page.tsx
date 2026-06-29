"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ThreadTable } from "@/components/forum/ThreadTable";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { PaginationBar } from "@/components/shared/pagination-bar";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import { ApiError } from "@/lib/api/client";
import { useAsyncAction } from "@/hooks/use-async-action";
import { usePagination } from "@/hooks/use-pagination";
import {
  type Category,
  type Forum,
  type ThreadSummary,
  browseThreads,
  getCategory,
  getForum,
  listCategories,
} from "@/lib/api/forum";
import {
  DOCUMENT_HUB_SLUG,
  isDocumentChildForumSlug,
} from "@/lib/forum-nav";

import { DEFAULT_PAGE_SIZE } from "@/lib/constants/pagination";

export default function CategoryThreadsPage() {
  const params = useParams();
  const forumSlug = typeof params.slug === "string" ? params.slug : "";
  const categorySlug = typeof params.categorySlug === "string" ? params.categorySlug : "";
  const { user } = useAuth();

  const [forum, setForum] = useState<Forum | null>(null);
  const [category, setCategory] = useState<Category | null>(null);
  const [parentCategory, setParentCategory] = useState<Category | null>(null);
  const [threads, setThreads] = useState<ThreadSummary[]>([]);
  const pagination = usePagination();
  const { loading, error, run } = useAsyncAction("Không tải được chủ đề");
  const [metaError, setMetaError] = useState<string | null>(null);

  useEffect(() => {
    if (!forumSlug || !categorySlug) return;
    Promise.all([getForum(forumSlug), getCategory(forumSlug, categorySlug), listCategories(forumSlug)])
      .then(([forumData, categoryData, allCategories]) => {
        setForum(forumData);
        setCategory(categoryData);
        if (categoryData.parentId) {
          setParentCategory(allCategories.find((item) => item.id === categoryData.parentId) ?? null);
        }
      })
      .catch((err) =>
        setMetaError(err instanceof ApiError ? err.message : "Không tải được mục diễn đàn"),
      );
  }, [forumSlug, categorySlug]);

  const loadThreads = useCallback(() => {
    if (!forum || !category) return;
    setThreads([]);
    run(async () => {
      const result = await browseThreads({
        forumId: forum.id,
        categoryId: category.id,
        page: pagination.page,
        size: DEFAULT_PAGE_SIZE,
      });
      setThreads(result.items);
      pagination.updateFromResponse(result);
    });
  }, [forum, category, pagination.page, run]);

  useEffect(() => {
    loadThreads();
  }, [loadThreads]);

  const forumTitleById = forum ? { [forum.id]: forum.title } : {};

  return (
    <div className="space-y-5">
      <PromoBanner />

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <nav className="mb-2 flex flex-wrap items-center gap-1 text-sm text-slate-500">
            <Link href="/" className="hover:text-fuo-600">
              Trang chủ
            </Link>
            <span>/</span>
            {isDocumentChildForumSlug(forumSlug) && (
              <>
                <Link href={`/forums/${DOCUMENT_HUB_SLUG}`} className="hover:text-fuo-600">
                  Tài liệu
                </Link>
                <span>/</span>
              </>
            )}
            <Link href={`/forums/${forumSlug}`} className="hover:text-fuo-600">
              {forum?.title ?? forumSlug}
            </Link>
            {parentCategory && (
              <>
                <span>/</span>
                <span>{parentCategory.title}</span>
              </>
            )}
            <span>/</span>
            <span className="font-medium text-slate-700">{category?.title ?? categorySlug}</span>
          </nav>
          <h1 className="text-2xl font-bold text-slate-900">{category?.title ?? categorySlug}</h1>
        </div>
        {!user ? (
          <Link href="/login" className="btn-primary">
            Đăng nhập để đăng bài
          </Link>
        ) : can(user, "forum.thread:create") ? (
          <Link href={`/forums/${forumSlug}/${categorySlug}/post`} className="btn-primary">
            Đăng bài
          </Link>
        ) : (
          <Link href="/membership" className="btn-primary">
            Nâng cấp để đăng bài
          </Link>
        )}
      </div>

      <div className="card overflow-hidden">
        <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
            Chủ đề
          </h2>
          <span className="text-xs text-slate-500">{pagination.totalElements} chủ đề</span>
        </div>

        <ErrorBanner message={metaError ?? error} />

        {loading ? (
          <LoadingState message="Đang tải chủ đề..." className="px-4 py-8" />
        ) : (
          <ThreadTable
            threads={threads}
            forumTitleById={forumTitleById}
            emptyMessage="Chưa có bài đăng trong mục này. Hãy là người đầu tiên đăng tài liệu!"
          />
        )}

        <PaginationBar
          page={pagination.page}
          totalPages={pagination.totalPages}
          onPageChange={pagination.setPage}
        />
      </div>
    </div>
  );
}
