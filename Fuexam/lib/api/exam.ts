import { API_V1 } from "@/lib/constants/api";
import { API_BASE, apiFetch } from "@/lib/api/client";

export interface PublicSubjectCard {
  id: string;
  code: string;
  title: string;
  categorySlug: string | null;
  cardColor: string | null;
  coverImageUrl: string | null;
  viewCount: number;
  feQuestionCount: number;
  pePaperCount: number;
}

export interface PublicSubjectDetail extends PublicSubjectCard {
  description: string | null;
  fePreviewCount: number;
  hasActiveMembership: boolean;
}

export interface PublicFeOption {
  id: string;
  optionText: string | null;
  optionImageUrl: string | null;
  isCorrect: boolean;
}

export interface PublicFeQuestion {
  id: string;
  questionText: string | null;
  questionImageUrls: string[] | null;
  explanation: string | null;
  multipleCorrect: boolean;
  sortOrder: number;
  preview: boolean;
  options: PublicFeOption[];
}

export interface PublicFeQuestionList {
  locked: boolean;
  totalCount: number;
  previewCount: number;
  questions: PublicFeQuestion[];
}

export interface PublicPeResource {
  id: string;
  folderLabel: string | null;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  sortOrder: number;
  downloadUrl: string;
}

export interface PublicPeItem {
  id: string;
  title: string;
  description: string | null;
  examImageUrls: string[] | null;
  sortOrder: number;
  resources: PublicPeResource[];
}

export type ExamSubjectType = "fe_question" | "pe_item";

export interface ExamComment {
  id: string;
  subjectType: ExamSubjectType;
  subjectId: string;
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

export function getExamFeQuestions(idOrCode: string) {
  return apiFetch<PublicFeQuestionList>(
    `${API_V1}/exam/catalog/${encodeURIComponent(idOrCode)}/fe`,
  );
}

export function getExamPeItems(idOrCode: string) {
  return apiFetch<PublicPeItem[]>(`${API_V1}/exam/catalog/${encodeURIComponent(idOrCode)}/pe`);
}

export function listExamComments(subjectType: ExamSubjectType, subjectId: string) {
  return apiFetch<ExamComment[]>(
    `${API_V1}/exam/comments/${subjectType}/${encodeURIComponent(subjectId)}`,
  );
}

export function createExamComment(
  subjectType: ExamSubjectType,
  subjectId: string,
  body: string,
  parentCommentId?: string,
) {
  const payload: Record<string, unknown> = { body };
  if (parentCommentId) payload.parentCommentId = parentCommentId;
  return apiFetch<ExamComment>(
    `${API_V1}/exam/comments/${subjectType}/${encodeURIComponent(subjectId)}`,
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
  );
}

export function updateExamComment(commentId: string, body: string) {
  return apiFetch<ExamComment>(`${API_V1}/exam/comments/${encodeURIComponent(commentId)}`, {
    method: "PUT",
    body: JSON.stringify({ body }),
  });
}

export function deleteExamComment(commentId: string) {
  return apiFetch<void>(`${API_V1}/exam/comments/${encodeURIComponent(commentId)}`, {
    method: "DELETE",
  });
}

/**
 * The backend returns a member-gated relative download path
 * (e.g. `/api/v1/exam/pe/resources/{id}/download`). Prefix the API base so the
 * anchor points at the backend origin.
 */
export function peResourceDownloadUrl(downloadUrl: string): string {
  if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) return downloadUrl;
  return `${API_BASE}${downloadUrl}`;
}
