import { API_V1 } from "@/lib/constants/api";
import { apiFetch } from "@/lib/api/client";

export type UploadPurpose =
  | "avatar"
  | "forum_image"
  | "forum_attachment"
  | "source_question"
  | "source_cover"
  | "coursera_cover"
  | "membership_plan"
  | "award_icon";

export interface UploadResponse {
  fileId: string;
  objectKey: string;
  publicUrl: string | null;
  mimeType: string;
  sizeBytes: number;
  originalFilename: string;
}

export async function uploadMedia(file: File, purpose: UploadPurpose): Promise<UploadResponse> {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch<UploadResponse>(`${API_V1}/media/uploads?purpose=${encodeURIComponent(purpose)}`, {
    method: "POST",
    body: formData,
  });
}

export function mediaDownloadUrl(fileId: string): string {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  return `${base}${API_V1}/media/files/${fileId}`;
}

export function resolveMediaUrl(storedReference: string | null | undefined): string | null {
  if (!storedReference) return null;
  if (storedReference.startsWith("http://") || storedReference.startsWith("https://")) {
    return storedReference;
  }
  // Signed / proxied API paths (e.g. /api/v1/exam/media/..?sig=..) must go straight to the
  // backend, not the public/uploads base — otherwise the signature path gets mangled.
  if (storedReference.startsWith("/api/")) {
    const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
    return `${apiBase}${storedReference}`;
  }
  const publicBase = process.env.NEXT_PUBLIC_MEDIA_BASE_URL ?? process.env.NEXT_PUBLIC_S3_PUBLIC_BASE_URL;
  if (!publicBase) {
    const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
    return `${apiBase}/uploads/${storedReference.replace(/^\/uploads\//, "")}`;
  }
  const base = publicBase.replace(/\/$/, "");
  const key = storedReference.replace(/^\/uploads\//, "");
  return `${base}/${key}`;
}
