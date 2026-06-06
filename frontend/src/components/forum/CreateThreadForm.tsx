"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api/client";
import { createThread, type ThreadType } from "@/lib/api/forum";
import { MarkdownEditor } from "@/components/forum/MarkdownEditor";
import {
  CAMPUS_OPTIONS,
  MATERIAL_TYPE_OPTIONS,
  SEMESTER_OPTIONS,
  THREAD_TYPES,
} from "@/lib/forum-thread-types";

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
  const [threadType, setThreadType] = useState<ThreadType>("article");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [campus, setCampus] = useState("");
  const [semester, setSemester] = useState("");
  const [materialType, setMaterialType] = useState("");
  const [tags, setTags] = useState("");
  const [pollOptions, setPollOptions] = useState(["", ""]);
  const [watchThread, setWatchThread] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const thread = await createThread({
        categoryId,
        title,
        body,
        threadType,
        campus: campus || undefined,
        semester: semester || undefined,
        materialType: materialType || undefined,
        tags: tags || undefined,
        pollOptions:
          threadType === "poll"
            ? pollOptions.map((option) => option.trim()).filter(Boolean)
            : undefined,
        watchThread,
      });
      router.push(`/threads/${thread.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể đăng bài");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="card space-y-5 p-5">
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
            onClick={() => setThreadType(type.id)}
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
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Nhập tiêu đề bài đăng"
          required
          maxLength={300}
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-slate-700">Nội dung</label>
        <MarkdownEditor
          value={body}
          onChange={setBody}
          minHeight={220}
          placeholder="Nội dung bài viết (Markdown)..."
        />
      </div>

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
          value={campus}
          onChange={setCampus}
          options={CAMPUS_OPTIONS}
        />
        <FieldSelect
          label="Học kỳ"
          required
          value={semester}
          onChange={setSemester}
          options={SEMESTER_OPTIONS}
        />
        <FieldSelect
          label="Loại tài liệu"
          required
          value={materialType}
          onChange={setMaterialType}
          options={MATERIAL_TYPE_OPTIONS}
        />
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Thẻ</label>
          <input
            className="input-field"
            value={tags}
            onChange={(event) => setTags(event.target.value)}
            placeholder="Nhiều thẻ có thể được phân cách bởi dấu phẩy"
          />
        </div>
      </div>

      <label className="flex items-center gap-2 text-sm text-slate-600">
        <input
          type="checkbox"
          checked={watchThread}
          onChange={(event) => setWatchThread(event.target.checked)}
          className="rounded border-slate-300"
        />
        Theo dõi tin đăng này...
      </label>

      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>}

      <button type="submit" className="btn-primary" disabled={submitting}>
        {submitting ? "Đang đăng..." : "Đăng bài"}
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
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
  required?: boolean;
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
        required={required}
      >
        {options.map((option) => (
          <option key={option.value || "default"} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}
