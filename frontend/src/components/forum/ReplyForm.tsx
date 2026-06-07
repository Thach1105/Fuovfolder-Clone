"use client";

import { useState } from "react";
import { ApiError } from "@/lib/api/client";
import { createPost } from "@/lib/api/forum";
import { RichTextEditor } from "@/components/forum/RichTextEditor";
import { DocumentUploader, type StagedFile } from "@/components/media/DocumentUploader";

interface ReplyFormProps {
  threadId: string;
  parentPostId?: string;
  onPosted: () => void;
}

export function ReplyForm({ threadId, parentPostId, onPosted }: ReplyFormProps) {
  const [body, setBody] = useState("");
  const [attachments, setAttachments] = useState<StagedFile[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await createPost(
        threadId,
        body,
        parentPostId,
        attachments.map((file) => file.fileId),
      );
      setBody("");
      setAttachments([]);
      onPosted();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể gửi trả lời");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="border-t border-slate-100 p-4">
      <h3 className="mb-2 text-sm font-semibold text-slate-800">
        {parentPostId ? "Trả lời bình luận" : "Trả lời"}
      </h3>
      <RichTextEditor value={body} onChange={setBody} minHeight={120} />
      <div className="mt-3">
        <DocumentUploader value={attachments} onChange={setAttachments} />
      </div>
      {error && <p className="mt-2 text-sm text-red-700">{error}</p>}
      <button type="submit" className="btn-primary mt-3" disabled={submitting}>
        {submitting ? "Đang gửi..." : "Gửi trả lời"}
      </button>
    </form>
  );
}
