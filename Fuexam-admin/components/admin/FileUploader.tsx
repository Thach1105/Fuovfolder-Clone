"use client";

import { useRef, useState } from "react";
import { Upload, X, FileArchive } from "lucide-react";
import { Button } from "@/components/ui/button";

export interface UploadedFileInfo {
  objectKey: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
}

type Props = {
  /** Performs the actual upload and returns at least the storage objectKey. */
  onUpload: (file: File) => Promise<{ objectKey: string }>;
  /** Called after a successful upload with the file metadata. */
  onUploaded: (info: UploadedFileInfo) => void;
  label?: string;
  accept?: string;
  buttonLabel?: string;
};

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function FileUploader({
  onUpload,
  onUploaded,
  label = "Tệp",
  accept = ".zip",
  buttonLabel = "Tải tệp lên",
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploaded, setUploaded] = useState<UploadedFileInfo | null>(null);

  async function handleFile(file: File | null) {
    if (!file) return;
    setError(null);
    setUploading(true);
    try {
      const { objectKey } = await onUpload(file);
      const info: UploadedFileInfo = {
        objectKey,
        originalFilename: file.name,
        mimeType: file.type || "application/octet-stream",
        sizeBytes: file.size,
      };
      setUploaded(info);
      onUploaded(info);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Tải tệp thất bại.");
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  function clear() {
    setUploaded(null);
    setError(null);
  }

  return (
    <div className="space-y-2">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      {uploaded && (
        <div className="flex items-center gap-2 rounded-lg border border-border bg-muted/20 px-3 py-2 text-sm">
          <FileArchive className="h-4 w-4 shrink-0 text-muted-foreground" />
          <span className="min-w-0 flex-1 truncate">{uploaded.originalFilename}</span>
          <span className="shrink-0 text-xs text-muted-foreground">
            {formatSize(uploaded.sizeBytes)}
          </span>
          <button
            type="button"
            className="shrink-0 text-destructive hover:text-destructive/80"
            onClick={clear}
            aria-label="Xóa"
          >
            <X className="h-3.5 w-3.5" />
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
          {uploading ? (
            "Đang tải..."
          ) : (
            <>
              <Upload className="mr-2 h-4 w-4" />
              {uploaded ? "Đổi tệp" : buttonLabel}
            </>
          )}
        </Button>
      </div>
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}
