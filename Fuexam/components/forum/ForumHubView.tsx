import Link from "next/link";
import { ForumNavIcon } from "@/components/layout/ForumNavIcon";
import type { Forum } from "@/lib/api/forum";
import { DOCUMENT_CHILD_FORUMS } from "@/lib/forum-nav";

const ICON_BY_SLUG = Object.fromEntries(
  DOCUMENT_CHILD_FORUMS.map((forum) => [forum.slug, forum.icon]),
) as Record<string, "exam" | "textbook">;

export function ForumHubView({ forums }: { forums: Forum[] }) {
  if (forums.length === 0) {
    return (
      <div className="card p-6 text-sm text-slate-600">
        Chưa có khu vực tài liệu con nào.
      </div>
    );
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {forums.map((forum) => {
        const icon = ICON_BY_SLUG[forum.slug] ?? "documents";
        return (
          <Link
            key={forum.id}
            href={`/forums/${forum.slug}`}
            className="card block p-6 transition hover:border-fuo-200 hover:shadow-md"
          >
            <div className="mb-3 flex items-center gap-3 text-fuo-600">
              <span className="rounded-lg bg-fuo-50 p-2">
                <ForumNavIcon name={icon} />
              </span>
              <h2 className="text-lg font-semibold text-slate-900">{forum.title}</h2>
            </div>
            {forum.description && (
              <p className="text-sm leading-relaxed text-slate-600">{forum.description}</p>
            )}
            <p className="mt-4 text-sm font-medium text-fuo-600">Xem danh mục →</p>
          </Link>
        );
      })}
    </div>
  );
}
