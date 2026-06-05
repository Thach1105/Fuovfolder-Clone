"use client";



import Link from "next/link";

import { useEffect, useState } from "react";

import { useParams } from "next/navigation";

import { CategoryTreeView } from "@/components/forum/CategoryTreeView";

import { ThreadTable } from "@/components/forum/ThreadTable";

import { PromoBanner } from "@/components/layout/PromoBanner";

import { ApiError } from "@/lib/api/client";

import {

  type CategoryTreeNode,

  type Forum,

  type ThreadSummary,

  browseThreads,

  getForum,

  listCategoryTree,

} from "@/lib/api/forum";

import { DOCUMENT_FORUM_SLUGS } from "@/lib/forum-nav";



const PAGE_SIZE = 20;



export default function ForumDetailPage() {

  const params = useParams();

  const slug = typeof params.slug === "string" ? params.slug : "";



  const [forum, setForum] = useState<Forum | null>(null);

  const [tree, setTree] = useState<CategoryTreeNode[]>([]);

  const [threads, setThreads] = useState<ThreadSummary[]>([]);

  const [loading, setLoading] = useState(true);

  const [error, setError] = useState<string | null>(null);



  const isDocumentForum = DOCUMENT_FORUM_SLUGS.includes(

    slug as (typeof DOCUMENT_FORUM_SLUGS)[number],

  );



  useEffect(() => {

    if (!slug) return;

    setLoading(true);

    setError(null);



    const loadForum = getForum(slug);
    const loadTree = isDocumentForum
      ? listCategoryTree(slug)
      : Promise.resolve([] as CategoryTreeNode[]);

    Promise.all([loadForum, loadTree])
      .then(([forumData, treeData]) => {
        setForum(forumData);
        if (isDocumentForum) {
          setTree(treeData);
        }
      })

      .catch((err) =>

        setError(err instanceof ApiError ? err.message : "Không tải được diễn đàn"),

      )

      .finally(() => setLoading(false));

  }, [slug, isDocumentForum]);



  useEffect(() => {

    if (!forum || isDocumentForum) return;

    browseThreads({ forumId: forum.id, page: 0, size: PAGE_SIZE })

      .then((result) => setThreads(result.items))

      .catch(() => setThreads([]));

  }, [forum, isDocumentForum]);



  const forumTitleById = forum ? { [forum.id]: forum.title } : {};



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

            <span className="font-medium text-slate-700">{forum?.title ?? slug}</span>

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



      {error && (

        <p className="rounded-lg bg-red-50 px-4 py-2 text-sm text-red-800">{error}</p>

      )}



      {loading ? (

        <p className="text-sm text-slate-500">Đang tải...</p>

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

