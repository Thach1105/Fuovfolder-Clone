import Link from "next/link";
import type { CategoryTreeNode } from "@/lib/api/forum";
import { getInitial } from "@/lib/utils/text";
import { formatDateTime } from "@/lib/format-datetime";
import { threadTypeLabel } from "@/lib/forum-thread-types";

interface CategoryTreeViewProps {
  forumSlug: string;
  nodes: CategoryTreeNode[];
}

export function CategoryTreeView({ forumSlug, nodes }: CategoryTreeViewProps) {
  return (
    <div className="card overflow-hidden">
      <div className="grid grid-cols-[1fr_80px_80px_220px] gap-3 border-b border-slate-100 bg-slate-50 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
        <span>Môn học</span>
        <span className="text-center">Chủ đề</span>
        <span className="text-center">Bài viết</span>
        <span>Hoạt động mới nhất</span>
      </div>

      {nodes.map((node) => (
        <SemesterRow key={node.id} forumSlug={forumSlug} node={node} />
      ))}
    </div>
  );
}

function SemesterRow({ forumSlug, node }: { forumSlug: string; node: CategoryTreeNode }) {
  const badgeLabel = node.title.replace(/^Kỳ\s*/i, "") || node.title;

  return (
    <div className="grid grid-cols-1 border-b border-slate-100 last:border-b-0 lg:grid-cols-[1fr_80px_80px_220px] lg:gap-3">
      <div className="flex gap-3 px-4 py-4">
        <div
          className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg text-lg font-bold text-white"
          style={{ backgroundColor: node.iconColor ?? "#2563eb" }}
        >
          {badgeLabel.length <= 2 ? badgeLabel : badgeLabel.charAt(0)}
        </div>
        <div className="min-w-0 flex-1">
          <h3 className="font-semibold text-slate-900">{node.title}</h3>
          <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
            {node.children.map((child) => (
              <Link
                key={child.id}
                href={`/forums/${forumSlug}/${child.slug}`}
                className="inline-flex items-center gap-1 text-sm text-fuo-600 hover:underline"
              >
                <FolderIcon />
                {child.title}
              </Link>
            ))}
          </div>
        </div>
      </div>

      <div className="hidden items-center justify-center px-2 text-sm font-semibold text-emerald-600 lg:flex">
        {node.topicCount}
      </div>
      <div className="hidden items-center justify-center px-2 text-sm font-semibold text-rose-600 lg:flex">
        {node.postCount}
      </div>
      <div className="hidden items-center gap-2 px-4 py-4 lg:flex">
        {node.latestActivity ? (
          <>
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-fuo-50 text-xs font-bold text-fuo-700">
              {getInitial(node.latestActivity.authorHandle)}
            </span>
            <div className="min-w-0 text-xs">
              {node.latestActivity.threadType && (
                <span className="mb-0.5 inline-block rounded bg-rose-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-rose-700">
                  {threadTypeLabel(node.latestActivity.threadType)}
                </span>
              )}
              <Link
                href={`/threads/${node.latestActivity.threadId}`}
                className="block truncate font-medium text-slate-800 hover:text-fuo-600"
              >
                {node.latestActivity.threadTitle}
              </Link>
              <p className="truncate text-slate-500">
                {formatDateTime(node.latestActivity.postedAt)} ·{" "}
                {node.latestActivity.authorHandle ?? "Ẩn danh"}
              </p>
            </div>
          </>
        ) : (
          <span className="text-xs text-slate-400">Chưa có hoạt động</span>
        )}
      </div>

      <div className="flex items-center justify-between border-t border-slate-50 px-4 py-2 text-xs text-slate-500 lg:hidden">
        <span>{node.topicCount} chủ đề · {node.postCount} bài</span>
        {node.latestActivity && (
          <Link href={`/threads/${node.latestActivity.threadId}`} className="text-fuo-600">
            Xem mới nhất
          </Link>
        )}
      </div>
    </div>
  );
}

function FolderIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" className="text-amber-500">
      <path d="M10 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-8l-2-2z" />
    </svg>
  );
}
