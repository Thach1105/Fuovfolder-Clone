"use client";

import { useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { replySchema, type ReplyFormValues } from "@/lib/schemas/forum";
import { createPost } from "@/lib/api/forum";
import { ApiError } from "@/lib/api/client";
import { RichTextEditor } from "@/components/forum/RichTextEditor";
import { DocumentUploader, type StagedFile } from "@/components/media/DocumentUploader";
import { ErrorBanner } from "@/components/ui/error-banner";

interface ReplyFormProps {
  threadId: string;
  parentPostId?: string;
  onPosted: () => void;
}

export function ReplyForm({ threadId, parentPostId, onPosted }: ReplyFormProps) {
  const form = useForm<ReplyFormValues>({
    resolver: zodResolver(replySchema),
    defaultValues: { body: "" },
  });
  const [attachments, setAttachments] = useState<StagedFile[]>([]);

  async function onSubmit(values: ReplyFormValues) {
    try {
      await createPost(
        threadId,
        values.body,
        parentPostId,
        attachments.map((file) => file.fileId),
      );
      form.reset();
      setAttachments([]);
      onPosted();
    } catch (err) {
      form.setError("root", {
        message: err instanceof ApiError ? err.message : "Không thể gửi trả lời",
      });
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="border-t border-slate-100 p-4">
      <h3 className="mb-2 text-sm font-semibold text-slate-800">
        {parentPostId ? "Trả lời bình luận" : "Trả lời"}
      </h3>
      <Controller
        control={form.control}
        name="body"
        render={({ field }) => (
          <RichTextEditor value={field.value} onChange={field.onChange} minHeight={120} />
        )}
      />
      {form.formState.errors.body && (
        <p className="mt-1 text-xs text-rose-500">{form.formState.errors.body.message}</p>
      )}
      <div className="mt-3">
        <DocumentUploader value={attachments} onChange={setAttachments} />
      </div>
      <ErrorBanner message={form.formState.errors.root?.message} className="mt-2" />
      <button type="submit" className="btn-primary mt-3" disabled={form.formState.isSubmitting}>
        {form.formState.isSubmitting ? "Đang gửi..." : "Gửi trả lời"}
      </button>
    </form>
  );
}
