"use client";

import { useRef, useState } from "react";
import { uploadMedia, type UploadResponse } from "@/lib/api/media";

type StagedFile = {
  fileId: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
};

type Props = {
  value: StagedFile[];
  onChange: (files: StagedFile[]) => void;
  label?: string;
};

const MAX_BYTES = 20 * 1024 * 1024;

export function DocumentUploader({ value, onChange, label = "Tài liệu đính kèm" }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleFile(file: File | null) {
    if (!file) return;
    if (file.size > MAX_BYTES) {
      setError("Tệp tối đa 20MB");
      return;
    }
    setError(null);
    setUploading(true);
    try {
      const uploaded: UploadResponse = await uploadMedia(file, "forum_attachment");
      onChange([
        ...value,
        {
          fileId: uploaded.fileId,
          originalFilename: uploaded.originalFilename,
          mimeType: uploaded.mimeType,
          sizeBytes: uploaded.sizeBytes,
        },
      ]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload thất bại");
    } finally {
      setUploading(false);
    }
  }

  function removeFile(fileId: string) {
    onChange(value.filter((item) => item.fileId !== fileId));
  }

  return (
    <div className="space-y-2">
      <p className="text-sm font-medium text-slate-700">{label}</p>
      <p className="text-xs text-slate-500">PDF, DOCX, PPTX — tối đa 20MB mỗi file</p>
      {value.length > 0 && (
        <ul className="space-y-1 rounded-lg border border-slate-200 bg-slate-50 p-2 text-sm">
          {value.map((file) => (
            <li key={file.fileId} className="flex items-center justify-between gap-2">
              <span className="truncate text-slate-700">{file.originalFilename}</span>
              <button
                type="button"
                className="shrink-0 text-xs text-red-600 hover:underline"
                onClick={() => removeFile(file.fileId)}
              >
                Xóa
              </button>
            </li>
          ))}
        </ul>
      )}
      <div>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.docx,.pptx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.openxmlformats-officedocument.presentationml.presentation"
          className="hidden"
          onChange={(e) => handleFile(e.target.files?.[0] ?? null)}
        />
        <button
          type="button"
          className="btn-secondary text-sm"
          disabled={uploading || value.length >= 5}
          onClick={() => inputRef.current?.click()}
        >
          {uploading ? "Đang tải..." : "Thêm tài liệu"}
        </button>
      </div>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}

export type { StagedFile };
