"use client";

import { useRef, useState } from "react";
import { X, Upload, GripVertical } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  uploadMedia,
  resolveMediaUrl,
  type UploadPurpose,
} from "@/lib/api/media";

type Props = {
  label?: string;
  purpose: UploadPurpose;
  value: string[];
  onChange: (urls: string[]) => void;
  maxImages?: number;
  accept?: string;
};

export function MultiImageUploader({
  label = "Anh",
  purpose,
  value,
  onChange,
  maxImages,
  accept = "image/png,image/jpeg,image/webp,image/gif",
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return;

    const fileArray = Array.from(files);

    // Validate max images
    if (maxImages && value.length + fileArray.length > maxImages) {
      setError(`Toi da ${maxImages} anh`);
      return;
    }

    // Warn if > 20 images
    if (value.length + fileArray.length > 20) {
      setError("Can bao: Nhieu hon 20 anh co the anh huong hieu nang");
    }

    setError(null);
    setUploading(true);

    try {
      const uploadPromises = fileArray.map((file) => uploadMedia(file, purpose));
      const uploaded = await Promise.all(uploadPromises);
      const newUrls = uploaded.map((u) => u.objectKey);
      onChange([...value, ...newUrls]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Tai anh that bai.");
    } finally {
      setUploading(false);
    }
  }

  function removeImage(index: number) {
    onChange(value.filter((_, i) => i !== index));
  }

  function removeAll() {
    onChange([]);
  }

  function handleDragStart(index: number) {
    setDraggedIndex(index);
  }

  function handleDragOver(e: React.DragEvent, index: number) {
    e.preventDefault();
    if (draggedIndex === null || draggedIndex === index) return;

    const newUrls = [...value];
    const draggedUrl = newUrls[draggedIndex];
    newUrls.splice(draggedIndex, 1);
    newUrls.splice(index, 0, draggedUrl);

    onChange(newUrls);
    setDraggedIndex(index);
  }

  function handleDragEnd() {
    setDraggedIndex(null);
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <p className="text-xs font-medium text-muted-foreground">{label}</p>
        {value.length > 0 && (
          <button
            type="button"
            className="text-xs text-destructive hover:underline"
            onClick={removeAll}
          >
            Xoa tat ca
          </button>
        )}
      </div>

      {value.length > 0 && (
        <div className="grid grid-cols-3 gap-2">
          {value.map((url, index) => {
            const previewUrl = resolveMediaUrl(url);
            return (
              <div
                key={`${url}-${index}`}
                draggable
                onDragStart={() => handleDragStart(index)}
                onDragOver={(e) => handleDragOver(e, index)}
                onDragEnd={handleDragEnd}
                className="group relative cursor-move rounded-lg border border-border bg-muted/20"
              >
                <div className="absolute left-1 top-1 rounded bg-background/90 p-0.5 opacity-0 group-hover:opacity-100">
                  <GripVertical className="h-3 w-3 text-muted-foreground" />
                </div>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={previewUrl ?? undefined}
                  alt={`Anh ${index + 1}`}
                  loading="lazy"
                  className="h-24 w-full rounded-lg object-cover"
                />
                <button
                  type="button"
                  className="absolute right-1 top-1 rounded-full bg-background/90 p-1 opacity-0 group-hover:opacity-100"
                  onClick={() => removeImage(index)}
                >
                  <X className="h-3 w-3 text-destructive" />
                </button>
                <div className="absolute bottom-1 right-1 rounded bg-background/90 px-1.5 py-0.5 text-xs">
                  {index + 1}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <div>
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          multiple
          className="hidden"
          onChange={(e) => handleFiles(e.target.files)}
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
              {value.length > 0 ? "Them anh" : "Tai anh len"}
            </>
          )}
        </Button>
      </div>

      {error && <p className="text-xs text-destructive">{error}</p>}
      {value.length > 0 && (
        <p className="text-xs text-muted-foreground">
          {value.length} anh · Keo de sap xep
        </p>
      )}
    </div>
  );
}
