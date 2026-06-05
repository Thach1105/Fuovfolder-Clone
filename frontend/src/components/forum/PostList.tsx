import type { Post } from "@/lib/api/forum";
import { authorInitial } from "@/lib/api/forum";
import { formatDateTime } from "@/lib/format-datetime";

interface PostListProps {
  posts: Post[];
}

export function PostList({ posts }: PostListProps) {
  if (posts.length === 0) {
    return <p className="text-sm text-slate-500">Chưa có bài viết nào trong chủ đề này.</p>;
  }

  return (
    <div className="divide-y divide-slate-100">
      {posts.map((post, index) => (
        <article key={post.id} className="flex gap-4 p-4">
          <div className="hidden shrink-0 sm:block">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-fuo-50 text-sm font-bold text-fuo-700">
              {authorInitial(post.authorHandle)}
            </div>
            <p className="mt-2 max-w-[4.5rem] truncate text-center text-xs text-slate-600">
              {post.authorHandle ?? "Ẩn danh"}
            </p>
          </div>
          <div className="min-w-0 flex-1">
            <header className="mb-2 flex flex-wrap items-center gap-2 text-xs text-slate-500">
              <span className="font-medium text-slate-700 sm:hidden">
                {post.authorHandle ?? "Ẩn danh"}
              </span>
              <time dateTime={post.createdAt}>{formatDateTime(post.createdAt)}</time>
              <span>#{index + 1}</span>
              {post.editVersion > 1 && (
                <span className="rounded bg-slate-100 px-1.5 py-0.5">đã sửa</span>
              )}
            </header>
            <div
              className="prose prose-sm max-w-none text-slate-800 prose-a:text-fuo-600"
              dangerouslySetInnerHTML={{ __html: post.bodyHtml || "<p>(trống)</p>" }}
            />
            {post.sourceUrl && (
              <a
                href={post.sourceUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="mt-2 inline-block text-xs text-fuo-600 hover:underline"
              >
                Xem trên fuoverflow.com →
              </a>
            )}
          </div>
        </article>
      ))}
    </div>
  );
}
