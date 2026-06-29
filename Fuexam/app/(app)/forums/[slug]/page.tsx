"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { CategoryTreeView } from "@/components/forum/CategoryTreeView";
import { ForumHubView } from "@/components/forum/ForumHubView";
import { ThreadTable } from "@/components/forum/ThreadTable";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import {
  type CategoryTreeNode,
  type Forum,
  type ThreadSummary,
  browseThreads,
  getForum,
  listCategoryTree,
} from "@/lib/api/forum";
import {
  DOCUMENT_HUB_SLUG,
  isDocumentChildForumSlug,
  isDocumentHubSlug,
  LEGACY_DOCUMENT_HUB_SLUG,
} from "@/lib/forum-nav";

import { DEFAULT_PAGE_SIZE } from "@/lib/constants/pagination";
const DOCUMENT_HUB_TITLE = "Tài liệu";

export default function ForumDetailPage() {
  const params = useParams();
  const router = useRouter();
  const slug = typeof params.slug === "string" ? params.slug : "";

  const [forum, setForum] = useState<Forum | null>(null);
  const [tree, setTree] = useState<CategoryTreeNode[]>([]);
  const [threads, setThreads] = useState<ThreadSummary[]>([]);
  const { loading, error, run } = useAsyncAction("Không tải được diễn đàn");

  const isHub = isDocumentHubSlug(slug);
  const isDocumentForum = isDocumentChildForumSlug(slug);

  useEffect(() => {
    if (slug === LEGACY_DOCUMENT_HUB_SLUG) {
      router.replace(`/forums/${DOCUMENT_HUB_SLUG}`);
    }
  }, [slug, router]);

  useEffect(() => {
    if (!slug || slug === LEGACY_DOCUMENT_HUB_SLUG) return;

    run(async () => {
      const loadForum = getForum(slug);
      const loadTree = isDocumentForum
        ? listCategoryTree(slug)
        : Promise.resolve([] as CategoryTreeNode[]);

      const [forumData, treeData] = await Promise.all([loadForum, loadTree]);
      setForum(forumData);
      if (isDocumentForum) {
        setTree(treeData);
      }
    });
  }, [slug, isDocumentForum, run]);

  useEffect(() => {
    if (!forum || isDocumentForum || isHub) return;

    browseThreads({ forumId: forum.id, page: 0, size: DEFAULT_PAGE_SIZE })
      .then((result) => setThreads(result.items))
      .catch(() => setThreads([]));
  }, [forum, isDocumentForum, isHub]);

  const forumTitleById = forum ? { [forum.id]: forum.title } : {};
  const hubChildren = forum?.children ?? [];

  return (
    <div className="space-y-5">
      <PromoBanner />

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <nav className="mb-2 flex items-center gap-1 text-sm text-slate-500">
            <Link href="/" className="hover:text-fuo-600">
              Trang chủ
            </Link>
            <span>/</span>
            {isDocumentChildForumSlug(slug) ? (
              <>
                <Link href={`/forums/${DOCUMENT_HUB_SLUG}`} className="hover:text-fuo-600">
                  {DOCUMENT_HUB_TITLE}
                </Link>
                <span>/</span>
                <span className="font-medium text-slate-700">{forum?.title ?? slug}</span>
              </>
            ) : (
              <span className="font-medium text-slate-700">{forum?.title ?? slug}</span>
            )}
          </nav>
          <h1 className="text-2xl font-bold text-slate-900">{forum?.title ?? slug}</h1>
          {forum?.description && (
            <p className="mt-1 text-sm text-slate-600">{forum.description}</p>
          )}
        </div>
        <Link href="/forums" className="text-sm font-medium text-fuo-600 hover:underline">
          ← Tất cả diễn đàn
        </Link>
      </div>

      <ErrorBanner message={error} />

      {loading ? (
        <LoadingState />
      ) : isHub ? (
        <ForumHubView forums={hubChildren} />
      ) : isDocumentForum ? (
        <CategoryTreeView forumSlug={slug} nodes={tree} />
      ) : (
        <div className="card overflow-hidden">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
              Chủ đề
            </h2>
          </div>
          <ThreadTable
            threads={threads}
            forumTitleById={forumTitleById}
            emptyMessage="Chưa có chủ đề trong diễn đàn này."
          />
        </div>
      )}
    </div>
  );
}
