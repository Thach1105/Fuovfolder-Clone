"use client";

import { useState } from "react";
import { ApiError } from "@/lib/api/client";
import { deletePost, type Post, updatePost } from "@/lib/api/forum";
import { authorInitial } from "@/lib/api/forum";
import { formatDateTime } from "@/lib/format-datetime";
import { RichTextEditor } from "@/components/forum/RichTextEditor";
import { PostReactions } from "@/components/forum/PostReactions";
import { ReportContentDialog } from "@/components/forum/ReportContentDialog";
import { ReplyForm } from "@/components/forum/ReplyForm";
import { mediaDownloadUrl } from "@/lib/api/media";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

interface PostItemProps {
  post: Post;
  index: number;
  threadId: string;
  nested?: boolean;
  onChanged: () => void;
}

export function PostItem({ post, index, threadId, nested = false, onChanged }: PostItemProps) {
  const { user } = useAuth();
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState("");
  const [replyOpen, setReplyOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isOwner = user?.id === post.authorUserId;
  const canEdit = isOwner && can(user, "forum.post:update");
  const canDelete = (isOwner && can(user, "forum.post:delete")) || can(user, "forum.post:moderate");
  const canReply = can(user, "forum.post:create") && !nested;

  async function handleSave(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await updatePost(threadId, post.id, body);
      setEditing(false);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể lưu bài viết");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!confirm("Xóa bài viết này?")) return;
    setSubmitting(true);
    setError(null);
    try {
      await deletePost(threadId, post.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể xóa bài viết");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <article className={`flex gap-4 p-4 ${nested ? "border-l-2 border-slate-200 pl-6" : ""}`}>
      <div className="hidden shrink-0 sm:block">
        {post.authorAvatarUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={post.authorAvatarUrl}
            alt=""
            className="h-12 w-12 rounded-full border border-slate-200 object-cover"
          />
        ) : (
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-fuo-50 text-sm font-bold text-fuo-700">
            {authorInitial(post.authorHandle)}
          </div>
        )}
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
          {post.status === "pending" && (
            <span className="rounded bg-amber-100 px-1.5 py-0.5 text-amber-800">Chờ duyệt</span>
          )}
          {post.editVersion > 1 && (
            <span className="rounded bg-slate-100 px-1.5 py-0.5">đã sửa</span>
          )}
        </header>

        {editing ? (
          <form onSubmit={handleSave} className="space-y-3">
            <RichTextEditor value={body} onChange={setBody} />
            {error && <p className="text-sm text-red-700">{error}</p>}
            <div className="flex gap-2">
              <button type="submit" className="btn-primary" disabled={submitting}>
                {submitting ? "Đang lưu..." : "Lưu"}
              </button>
              <button type="button" className="btn-secondary" onClick={() => setEditing(false)}>
                Hủy
              </button>
            </div>
          </form>
        ) : (
          <div
            className="prose prose-sm max-w-none text-slate-800 prose-a:text-fuo-600"
            dangerouslySetInnerHTML={{ __html: post.bodyHtml || "<p>(trống)</p>" }}
          />
        )}

        {post.attachments?.length > 0 && (
          <div className="mt-3 rounded-lg border border-slate-100 bg-slate-50 p-3">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
              Tài liệu đính kèm
            </p>
            <ul className="space-y-1 text-sm">
              {post.attachments.map((attachment) => (
                <li key={attachment.fileId}>
                  <a
                    href={mediaDownloadUrl(attachment.fileId)}
                    className="font-medium text-fuo-600 hover:underline"
                  >
                    {attachment.originalFilename}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        )}

        {!editing && (
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <PostReactions postId={post.id} />
            {canReply && (
              <button
                type="button"
                className="text-xs font-medium text-fuo-600 hover:underline"
                onClick={() => setReplyOpen((value) => !value)}
              >
                Trả lời
              </button>
            )}
            {canEdit && (
              <button
                type="button"
                className="text-xs font-medium text-slate-500 hover:text-slate-700"
                onClick={() => {
                  setBody(post.bodyHtml || post.bodyMd);
                  setEditing(true);
                }}
              >
                Sửa
              </button>
            )}
            {canDelete && (
              <button
                type="button"
                className="text-xs font-medium text-red-600 hover:underline"
                onClick={handleDelete}
                disabled={submitting}
              >
                Xóa
              </button>
            )}
            <ReportContentDialog targetType="post" targetId={post.id} />
          </div>
        )}

        {replyOpen && canReply && (
          <div className="mt-3 rounded-lg border border-slate-100 bg-slate-50 p-3">
            <p className="mb-2 text-xs text-slate-500">
              Trả lời @{post.authorHandle ?? "thành viên"}
            </p>
            <ReplyForm
              threadId={threadId}
              parentPostId={post.id}
              onPosted={() => {
                setReplyOpen(false);
                onChanged();
              }}
            />
          </div>
        )}

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
  );
}
