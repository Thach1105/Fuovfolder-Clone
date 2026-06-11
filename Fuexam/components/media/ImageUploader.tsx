"use client";

import { useRef, useState } from "react";
import { uploadMedia, resolveMediaUrl, type UploadPurpose, type UploadResponse } from "@/lib/api/media";

type Props = {
  purpose: UploadPurpose;
  value: string | null;
  onChange: (objectKey: string | null, uploaded?: UploadResponse) => void;
  label?: string;
  accept?: string;
};

export function ImageUploader({
  purpose,
  value,
  onChange,
  label = "Ảnh",
  accept = "image/png,image/jpeg,image/webp,image/gif",
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleFile(file: File | null) {
    if (!file) return;
    setError(null);
    setUploading(true);
    try {
      const uploaded = await uploadMedia(file, purpose);
      onChange(uploaded.objectKey, uploaded);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload thất bại");
    } finally {
      setUploading(false);
    }
  }

  const previewUrl = resolveMediaUrl(value);

  return (
    <div className="space-y-2">
      <p className="eyebrow">{label}</p>
      {previewUrl && (
        <div className="relative inline-block">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={previewUrl} alt="" className="max-h-32 rounded-xl border border-ink-200 object-cover" />
          <button
            type="button"
            className="absolute right-1.5 top-1.5 rounded-full bg-white/90 px-2 py-0.5 text-xs font-medium text-red-600 shadow-sm hover:bg-white"
            onClick={() => onChange(null)}
          >
            Xóa
          </button>
        </div>
      )}
      <div className="flex gap-2">
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          className="hidden"
          onChange={(e) => handleFile(e.target.files?.[0] ?? null)}
        />
        <button
          type="button"
          className="btn-secondary text-xs"
          disabled={uploading}
          onClick={() => inputRef.current?.click()}
        >
          {uploading ? "Đang tải..." : previewUrl ? "Đổi ảnh" : "Tải ảnh lên"}
        </button>
      </div>
      {error && <p className="text-xs text-red-500">{error}</p>}
    </div>
  );
}