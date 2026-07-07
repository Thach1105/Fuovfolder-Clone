"use client";

import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { ErrorBanner } from "@/components/ui/error-banner";
import { UserAvatar } from "@/components/shared/user-avatar";
import { ApiError } from "@/lib/api/client";
import { resolveMediaUrl } from "@/lib/api/media";
import { formatDateTime } from "@/lib/format-datetime";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  type ExamComment,
  type ExamSubjectType,
  createExamComment,
  deleteExamComment,
  listExamComments,
  updateExamComment,
} from "@/lib/api/exam";

type Props = { subjectType: ExamSubjectType; subjectId: string };

function commentAuthorName(comment: ExamComment) {
  return comment.authorDisplayName ?? comment.authorUsername ?? "Người dùng";
}

function CommentCard({
  comment,
  onReply,
  onEdit,
  onDelete,
  busy,
}: {
  comment: ExamComment;
  onReply: (comment: ExamComment) => void;
  onEdit: (comment: ExamComment) => void;
  onDelete: (comment: ExamComment) => void;
  busy: boolean;
}) {
  const name = commentAuthorName(comment);
  return (
    <div className="flex gap-3">
      <UserAvatar src={resolveMediaUrl(comment.authorAvatarUrl)} displayName={name} size="sm" />
      <div className="flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">{name}</span>
          <span className="font-mono text-[11px] text-muted-foreground">
            {formatDateTime(comment.createdAt)}
            {comment.updatedAt !== comment.createdAt ? " (đã sửa)" : ""}
          </span>
        </div>
        <div
          className="prose prose-sm max-w-none text-sm text-foreground/90 [&_a]:text-sky-600 [&_a]:underline"
          // Backend sanitizes bodyHtml before returning it.
          dangerouslySetInnerHTML={{ __html: comment.bodyHtml }}
        />
        <div className="flex flex-wrap items-center gap-3 pt-0.5 text-xs text-muted-foreground">
          {!comment.parentCommentId && (
            <button type="button" className="hover:text-foreground" onClick={() => onReply(comment)}>
              Trả lời
            </button>
          )}
          {comment.editable && (
            <>
              <button
                type="button"
                className="hover:text-foreground"
                onClick={() => onEdit(comment)}
                disabled={busy}
              >
                Sửa
              </button>
              <button
                type="button"
                className="hover:text-destructive"
                onClick={() => onDelete(comment)}
                disabled={busy}
              >
                Xóa
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export function ExamCommentThread({ subjectType, subjectId }: Props) {
  const { user } = useAuth();
  const [comments, setComments] = useState<ExamComment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [body, setBody] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [replyTo, setReplyTo] = useState<ExamComment | null>(null);
  const [editing, setEditing] = useState<ExamComment | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setComments(await listExamComments(subjectType, subjectId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được bình luận");
    } finally {
      setLoading(false);
    }
  }, [subjectType, subjectId]);

  useEffect(() => {
    load();
  }, [load]);

  function resetForm() {
    setBody("");
    setReplyTo(null);
    setEditing(null);
  }

  async function handleSubmit() {
    const trimmed = body.trim();
    if (!trimmed) return;
    setSubmitting(true);
    setError(null);
    try {
      if (editing) {
        await updateExamComment(editing.id, trimmed);
      } else {
        await createExamComment(subjectType, subjectId, trimmed, replyTo?.id);
      }
      resetForm();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không gửi được bình luận");
    } finally {
      setSubmitting(false);
    }
  }

  function startEdit(comment: ExamComment) {
    setEditing(comment);
    setReplyTo(null);
    // bodyHtml is sanitized HTML; strip tags for a plain-text edit surface.
    const plain = comment.bodyHtml.replace(/<[^>]+>/g, "").trim();
    setBody(plain);
  }

  function startReply(comment: ExamComment) {
    setEditing(null);
    setReplyTo(comment);
  }

  async function handleDelete(comment: ExamComment) {
    setBusyId(comment.id);
    setError(null);
    try {
      await deleteExamComment(comment.id);
      if (editing?.id === comment.id || replyTo?.id === comment.id) resetForm();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không xóa được bình luận");
    } finally {
      setBusyId(null);
    }
  }

  const topLevel = comments.filter((c) => !c.parentCommentId);
  const repliesByParent = comments.reduce<Record<string, ExamComment[]>>((acc, c) => {
    if (c.parentCommentId) (acc[c.parentCommentId] ??= []).push(c);
    return acc;
  }, {});

  return (
    <div className="space-y-4 rounded-2xl border border-foreground/10 bg-background/60 p-5 backdrop-blur">
      <h3 className="font-display text-base">Bình luận ({comments.length})</h3>

      <ErrorBanner message={error} />

      <div className="space-y-2">
        {replyTo && (
          <p className="flex items-center justify-between text-xs text-muted-foreground">
            <span>Đang trả lời {commentAuthorName(replyTo)}</span>
            <button type="button" className="hover:text-foreground" onClick={() => setReplyTo(null)}>
              Hủy
            </button>
          </p>
        )}
        {editing && (
          <p className="flex items-center justify-between text-xs text-muted-foreground">
            <span>Đang chỉnh sửa bình luận</span>
            <button type="button" className="hover:text-foreground" onClick={resetForm}>
              Hủy
            </button>
          </p>
        )}
        <div className="flex gap-3">
          {user && (
            <UserAvatar src={resolveMediaUrl(user.avatarUrl)} displayName={user.displayName} size="sm" />
          )}
          <div className="flex-1 space-y-2">
            <Textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="Viết bình luận..."
              className="min-h-20"
            />
            <div className="flex justify-end gap-2">
              {(editing || replyTo) && (
                <Button variant="ghost" size="sm" className="rounded-full" onClick={resetForm}>
                  Hủy
                </Button>
              )}
              <Button
                size="sm"
                className="rounded-full bg-foreground text-background hover:bg-foreground/90"
                disabled={submitting || !body.trim()}
                onClick={handleSubmit}
              >
                {submitting ? "Đang gửi..." : editing ? "Lưu" : "Gửi"}
              </Button>
            </div>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="space-y-3">
          {Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="app-skeleton h-14 w-full rounded-xl" />
          ))}
        </div>
      ) : topLevel.length === 0 ? (
        <p className="text-sm text-muted-foreground">Chưa có bình luận. Hãy là người đầu tiên.</p>
      ) : (
        <div className="space-y-5">
          {topLevel.map((comment) => (
            <div key={comment.id} className="space-y-3">
              <CommentCard
                comment={comment}
                onReply={startReply}
                onEdit={startEdit}
                onDelete={handleDelete}
                busy={busyId === comment.id}
              />
              {(repliesByParent[comment.id] ?? []).length > 0 && (
                <div className="ml-11 space-y-3 border-l border-foreground/10 pl-4">
                  {repliesByParent[comment.id].map((reply) => (
                    <CommentCard
                      key={reply.id}
                      comment={reply}
                      onReply={startReply}
                      onEdit={startEdit}
                      onDelete={handleDelete}
                      busy={busyId === reply.id}
                    />
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
