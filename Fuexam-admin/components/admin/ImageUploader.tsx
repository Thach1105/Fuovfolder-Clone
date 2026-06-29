"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  uploadMedia,
  resolveMediaUrl,
  type UploadPurpose,
  type UploadResponse,
} from "@/lib/api/media";

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
      setError(err instanceof Error ? err.message : "Tải ảnh thất bại.");
    } finally {
      setUploading(false);
    }
  }

  const previewUrl = resolveMediaUrl(value);

  return (
    <div className="space-y-2">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      {previewUrl && (
        <div className="relative inline-block">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={previewUrl}
            alt=""
            loading="lazy"
            className="max-h-32 rounded-lg border border-border object-cover"
          />
          <button
            type="button"
            className="absolute right-1.5 top-1.5 rounded-full bg-background/90 px-2 py-0.5 text-xs font-medium text-destructive shadow-sm hover:bg-background"
            onClick={() => onChange(null)}
          >
            Xóa
          </button>
        </div>
      )}
      <div>
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          className="hidden"
          onChange={(e) => handleFile(e.target.files?.[0] ?? null)}
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={uploading}
          onClick={() => inputRef.current?.click()}
        >
          {uploading ? "Đang tải..." : previewUrl ? "Đổi ảnh" : "Tải ảnh lên"}
        </Button>
      </div>
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}
