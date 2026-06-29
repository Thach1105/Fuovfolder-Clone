"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { createThreadSchema, type CreateThreadFormValues } from "@/lib/schemas/forum";
import { createThread } from "@/lib/api/forum";
import { ApiError } from "@/lib/api/client";
import { RichTextEditor } from "@/components/forum/RichTextEditor";
import { DocumentUploader, type StagedFile } from "@/components/media/DocumentUploader";
import {
  CAMPUS_OPTIONS,
  MATERIAL_TYPE_OPTIONS,
  SEMESTER_OPTIONS,
  THREAD_TYPES,
} from "@/lib/forum-thread-types";
import { ErrorBanner } from "@/components/ui/error-banner";

interface CreateThreadFormProps {
  categoryId: string;
  forumSlug: string;
  categoryTitle: string;
  parentTitle?: string | null;
}

export function CreateThreadForm({
  categoryId,
  forumSlug,
  categoryTitle,
  parentTitle,
}: CreateThreadFormProps) {
  const router = useRouter();
  const form = useForm<CreateThreadFormValues>({
    resolver: zodResolver(createThreadSchema),
    defaultValues: {
      threadType: "article",
      title: "",
      body: "",
      campus: "",
      semester: "",
      materialType: "",
      tags: "",
      watchThread: true,
    },
  });
  const [pollOptions, setPollOptions] = useState(["", ""]);
  const [attachments, setAttachments] = useState<StagedFile[]>([]);

  const threadType = form.watch("threadType");

  async function onSubmit(values: CreateThreadFormValues) {
    try {
      const thread = await createThread({
        categoryId,
        ...values,
        campus: values.campus || undefined,
        semester: values.semester || undefined,
        materialType: values.materialType || undefined,
        tags: values.tags || undefined,
        pollOptions:
          values.threadType === "poll"
            ? pollOptions.map((option) => option.trim()).filter(Boolean)
            : undefined,
        attachmentFileIds: attachments.map((file) => file.fileId),
      });
      router.push(`/threads/${thread.id}`);
    } catch (err) {
      form.setError("root", {
        message: err instanceof ApiError ? err.message : "Không thể đăng bài",
      });
    }
  }

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} className="card space-y-5 p-5">
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Đăng bài</p>
        <h1 className="text-xl font-bold text-slate-900">
          {parentTitle ? `${parentTitle} · ${categoryTitle}` : categoryTitle}
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          Diễn đàn:{" "}
          <span className="font-medium text-slate-700">{forumSlug}</span>
        </p>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-slate-100 pb-3">
        {THREAD_TYPES.map((type) => (
          <button
            key={type.id}
            type="button"
            onClick={() => form.setValue("threadType", type.id)}
            className={
              threadType === type.id
                ? "rounded-lg bg-fuo-600 px-4 py-2 text-sm font-medium text-white"
                : "rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
            }
          >
            {type.label}
          </button>
        ))}
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-700">Tựa đề</label>
        <input
          className="input-field"
          {...form.register("title")}
          placeholder="Nhập tiêu đề bài đăng"
          maxLength={300}
        />
        {form.formState.errors.title && (
          <p className="mt-1 text-xs text-rose-500">{form.formState.errors.title.message}</p>
        )}
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-700">Nội dung</label>
        <Controller
          control={form.control}
          name="body"
          render={({ field }) => (
            <RichTextEditor
              value={field.value}
              onChange={field.onChange}
              minHeight={220}
              placeholder="Nội dung bài viết..."
            />
          )}
        />
        {form.formState.errors.body && (
          <p className="mt-1 text-xs text-rose-500">{form.formState.errors.body.message}</p>
        )}
      </div>

      <DocumentUploader value={attachments} onChange={setAttachments} />

      {threadType === "poll" && (
        <div className="space-y-2 rounded-lg border border-slate-100 bg-slate-50 p-4">
          <p className="text-sm font-medium text-slate-700">Lựa chọn bình chọn</p>
          {pollOptions.map((option, index) => (
            <input
              key={index}
              className="input-field"
              value={option}
              onChange={(event) => {
                const next = [...pollOptions];
                next[index] = event.target.value;
                setPollOptions(next);
              }}
              placeholder={`Lựa chọn ${index + 1}`}
            />
          ))}
          <button
            type="button"
            className="btn-secondary text-xs"
            onClick={() => setPollOptions((items) => [...items, ""])}
          >
            + Thêm lựa chọn
          </button>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <FieldSelect
          label="Cơ sở"
          required
          value={form.watch("campus")}
          onChange={(v) => form.setValue("campus", v)}
          options={CAMPUS_OPTIONS}
          error={form.formState.errors.campus?.message}
        />
        <FieldSelect
          label="Học kỳ"
          required
          value={form.watch("semester")}
          onChange={(v) => form.setValue("semester", v)}
          options={SEMESTER_OPTIONS}
          error={form.formState.errors.semester?.message}
        />
        <FieldSelect
          label="Loại tài liệu"
          required
          value={form.watch("materialType")}
          onChange={(v) => form.setValue("materialType", v)}
          options={MATERIAL_TYPE_OPTIONS}
          error={form.formState.errors.materialType?.message}
        />
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Thẻ</label>
          <input
            className="input-field"
            {...form.register("tags")}
            placeholder="Nhiều thẻ có thể được phân cách bởi dấu phẩy"
          />
        </div>
      </div>

      <label className="flex items-center gap-2 text-sm text-slate-600">
        <input
          type="checkbox"
          {...form.register("watchThread")}
          className="rounded border-slate-300"
        />
        Theo dõi tin đăng này...
      </label>

      <ErrorBanner message={form.formState.errors.root?.message} />

      <button type="submit" className="btn-primary" disabled={form.formState.isSubmitting}>
        {form.formState.isSubmitting ? "Đang đăng..." : "Đăng bài"}
      </button>
    </form>
  );
}

function FieldSelect({
  label,
  value,
  onChange,
  options,
  required,
  error,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
  required?: boolean;
  error?: string;
}) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-slate-700">
        {label}
        {required && <span className="text-rose-500"> *</span>}
      </label>
      <select
        className="input-field"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value || "default"} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error && <p className="mt-1 text-xs text-rose-500">{error}</p>}
    </div>
  );
}
