"use client";

import { useState } from "react";
import { ApiError } from "@/lib/api/client";
import { createPost } from "@/lib/api/forum";
import { MarkdownEditor } from "@/components/forum/MarkdownEditor";

interface ReplyFormProps {
  threadId: string;
  parentPostId?: string;
  onPosted: () => void;
}

export function ReplyForm({ threadId, parentPostId, onPosted }: ReplyFormProps) {
  const [body, setBody] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await createPost(threadId, body, parentPostId);
      setBody("");
      onPosted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể gửi trả lời");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="border-t border-slate-100 p-4">
      <h3 className="mb-2 text-sm font-semibold text-slate-800">
        {parentPostId ? "Trả lời bình luận" : "Trả lời"}
      </h3>
      <MarkdownEditor value={body} onChange={setBody} minHeight={120} />
      {error && <p className="mt-2 text-sm text-red-700">{error}</p>}
      <button type="submit" className="btn-primary mt-3" disabled={submitting}>
        {submitting ? "Đang gửi..." : "Gửi trả lời"}
      </button>
    </form>
  );
}
