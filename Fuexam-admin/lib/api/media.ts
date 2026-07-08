import { API_BASE, apiFetch } from "@/lib/api/client";

export type UploadPurpose =
  | "avatar"
  | "forum_image"
  | "forum_attachment"
  | "source_question"
  | "source_cover"
  | "coursera_cover"
  | "membership_plan"
  | "award_icon"
  | "exam_paper_image"
  | "exam_paper_file";

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
  return apiFetch<UploadResponse>(`/api/v1/media/uploads?purpose=${encodeURIComponent(purpose)}`, {
    method: "POST",
    body: formData,
  });
}

export function mediaDownloadUrl(fileId: string): string {
  return `${API_BASE}/api/v1/media/files/${fileId}`;
}

export function resolveMediaUrl(storedReference: string | null | undefined): string | null {
  if (!storedReference) return null;
  if (storedReference.startsWith("http://") || storedReference.startsWith("https://")) {
    return storedReference;
  }
  const publicBase = process.env.NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL;
  const key = storedReference.replace(/^\/uploads\//, "");
  if (!publicBase) {
    // Served by the backend through the same-origin proxy.
    return `${API_BASE}/uploads/${key}`;
  }
  return `${publicBase.replace(/\/$/, "")}/${key}`;
}
