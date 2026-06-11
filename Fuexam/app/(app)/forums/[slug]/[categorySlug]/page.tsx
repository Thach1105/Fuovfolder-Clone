"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ThreadTable } from "@/components/forum/ThreadTable";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import { ApiError } from "@/lib/api/client";
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

const PAGE_SIZE = 20;

export default function CategoryThreadsPage() {
  const params = useParams();
  const forumSlug = typeof params.slug === "string" ? params.slug : "";
  const categorySlug = typeof params.categorySlug === "string" ? params.categorySlug : "";
  const { user } = useAuth();

  const [forum, setForum] = useState<Forum | null>(null);
  const [category, setCategory] = useState<Category | null>(null);
  const [parentCategory, setParentCategory] = useState<Category | null>(null);
  const [threads, setThreads] = useState<ThreadSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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
        setError(err instanceof ApiError ? err.message : "Không tải được mục diễn đàn"),
      );
  }, [forumSlug, categorySlug]);

  const loadThreads = useCallback(async () => {
    if (!forum || !category) return;
    setLoading(true);
    setError(null);
    try {
      const result = await browseThreads({
        forumId: forum.id,
        categoryId: category.id,
        page,
        size: PAGE_SIZE,
      });
      setThreads(result.items);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được chủ đề");
      setThreads([]);
    } finally {
      setLoading(false);
    }
  }, [forum, category, page]);

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
          <span className="text-xs text-slate-500">{totalElements} chủ đề</span>
        </div>

        {error && (
          <p className="border-b border-red-100 bg-red-50 px-4 py-2 text-sm text-red-800">
            {error}
          </p>
        )}

        {loading ? (
          <p className="px-4 py-8 text-sm text-slate-500">Đang tải chủ đề...</p>
        ) : (
          <ThreadTable
            threads={threads}
            forumTitleById={forumTitleById}
            emptyMessage="Chưa có bài đăng trong mục này. Hãy là người đầu tiên đăng tài liệu!"
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
