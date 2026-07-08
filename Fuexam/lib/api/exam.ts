import { API_V1 } from "@/lib/constants/api";
import { API_BASE, apiFetch } from "@/lib/api/client";

export type ExamPaperType = "FE" | "PE";

export interface PublicSubjectCard {
  id: string;
  code: string;
  title: string;
  categorySlug: string | null;
  cardColor: string | null;
  coverImageUrl: string | null;
  viewCount: number;
  fePaperCount: number;
  pePaperCount: number;
}

export interface PublicPaperSummary {
  id: string;
  type: ExamPaperType;
  term: string;
  retakeLabel: string | null;
  title: string;
  imageCount: number;
  resourceCount: number;
}

export interface PublicSubjectDetail {
  id: string;
  code: string;
  title: string;
  description: string | null;
  categorySlug: string | null;
  cardColor: string | null;
  coverImageUrl: string | null;
  viewCount: number;
  hasActiveMembership: boolean;
  papers: PublicPaperSummary[];
  related: PublicSubjectCard[];
}

export interface PublicPaperResource {
  id: string;
  folderLabel: string | null;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  sortOrder: number;
  downloadUrl: string;
}

export interface PublicPaperDetail {
  id: string;
  subjectId: string;
  subjectCode: string;
  type: ExamPaperType;
  term: string;
  retakeLabel: string | null;
  title: string;
  description: string | null;
  imageUrls: string[];
  resources: PublicPaperResource[];
}

export type ExamCommentSubjectType = "paper" | "paper_image";

export interface ExamComment {
  id: string;
  subjectType: ExamCommentSubjectType;
  subjectId: string;
  imageIndex: number | null;
  authorUserId: string;
  authorUsername: string | null;
  authorDisplayName: string | null;
  authorAvatarUrl: string | null;
  parentCommentId: string | null;
  bodyHtml: string;
  editable: boolean;
  createdAt: string;
  updatedAt: string;
}

export function listExamSubjects() {
  return apiFetch<PublicSubjectCard[]>(`${API_V1}/exam/catalog`);
}

export function getExamSubject(idOrCode: string) {
  return apiFetch<PublicSubjectDetail>(`${API_V1}/exam/catalog/${encodeURIComponent(idOrCode)}`);
}

export function getExamPaper(paperId: string) {
  return apiFetch<PublicPaperDetail>(
    `${API_V1}/exam/catalog/papers/${encodeURIComponent(paperId)}`,
  );
}

export function listPaperComments(paperId: string) {
  return apiFetch<ExamComment[]>(
    `${API_V1}/exam/comments/papers/${encodeURIComponent(paperId)}`,
  );
}

export function createPaperComment(paperId: string, body: string, parentCommentId?: string) {
  const payload: Record<string, unknown> = { body };
  if (parentCommentId) payload.parentCommentId = parentCommentId;
  return apiFetch<ExamComment>(
    `${API_V1}/exam/comments/papers/${encodeURIComponent(paperId)}`,
    { method: "POST", body: JSON.stringify(payload) },
  );
}

export function listPaperImageComments(paperId: string, imageIndex: number) {
  return apiFetch<ExamComment[]>(
    `${API_V1}/exam/comments/papers/${encodeURIComponent(paperId)}/images/${imageIndex}`,
  );
}

export function createPaperImageComment(
  paperId: string,
  imageIndex: number,
  body: string,
  parentCommentId?: string,
) {
  const payload: Record<string, unknown> = { body };
  if (parentCommentId) payload.parentCommentId = parentCommentId;
  return apiFetch<ExamComment>(
    `${API_V1}/exam/comments/papers/${encodeURIComponent(paperId)}/images/${imageIndex}`,
    { method: "POST", body: JSON.stringify(payload) },
  );
}

export function updateExamComment(commentId: string, body: string) {
  return apiFetch<ExamComment>(`${API_V1}/exam/comments/comments/${encodeURIComponent(commentId)}`, {
    method: "PUT",
    body: JSON.stringify({ body }),
  });
}

export function deleteExamComment(commentId: string) {
  return apiFetch<void>(`${API_V1}/exam/comments/comments/${encodeURIComponent(commentId)}`, {
    method: "DELETE",
  });
}

/**
 * The backend returns a member-gated relative download path
 * (e.g. `/api/v1/exam/papers/resources/{id}/download`). Prefix the API base so
 * the anchor points at the backend origin.
 */
export function paperResourceDownloadUrl(downloadUrl: string): string {
  if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) return downloadUrl;
  return `${API_BASE}${downloadUrl}`;
}
