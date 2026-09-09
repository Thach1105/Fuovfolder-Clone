import { API_V1 } from "@/lib/constants/api";
import { API_BASE, apiFetch } from "@/lib/api/client";

export type ExamPaperType = "FE" | "PE";

export interface PublicSubjectLatestPaper {
  examCode: string;
  paperType: ExamPaperType;
  createdAt: string;
}

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
  curriculumTerm: number | null;
  latestPaper: PublicSubjectLatestPaper | null;
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

export interface PublicImageItem {
  index: number;
  url: string | null;
  type: "full" | "blur";
}

export interface PublicFeQuestion {
  id: string;
  questionText: string | null;
  totalImageCount: number;
  images: PublicImageItem[];
  sortOrder: number;
  commentCount: number;
  viewCount: number;
  createdAt: string;
}

export interface PublicFeQuestionList {
  locked: boolean;
  totalCount: number;
  previewImageCount: number;
  questions: PublicFeQuestion[];
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
  likeCount: number;
  likedByMe: boolean;
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

export function listFeQuestions(idOrCode: string) {
  return apiFetch<PublicFeQuestionList>(
    `${API_V1}/exam/catalog/${encodeURIComponent(idOrCode)}/fe`,
  );
}

export function getExamPaper(paperId: string) {
  return apiFetch<PublicPaperDetail>(
    `${API_V1}/exam/catalog/papers/${encodeURIComponent(paperId)}`,
  );
}

export function incrementFeQuestionView(idOrCode: string, questionId: string) {
  return apiFetch<void>(
    `${API_V1}/exam/catalog/${encodeURIComponent(idOrCode)}/fe/${encodeURIComponent(questionId)}/view`,
    { method: "POST" },
  ).catch(() => {});
}

export interface ExamCommentLikeResult {
  liked: boolean;
  likeCount: number;
}

export function toggleExamCommentLike(commentId: string) {
  return apiFetch<ExamCommentLikeResult>(
    `${API_V1}/exam/comments/${encodeURIComponent(commentId)}/like`,
    { method: "POST" },
  );
}

/**
 * Correctly-routed comment endpoints matching ExamCommentController:
 * GET/POST /api/v1/exam/comments/{subjectType}/{subjectId}, where subjectType
 * is "fe_question" or "pe_item" and subjectId is the question/item id.
 */
export function listExamComments(subjectType: string, subjectId: string) {
  return apiFetch<ExamComment[]>(
    `${API_V1}/exam/comments/${encodeURIComponent(subjectType)}/${encodeURIComponent(subjectId)}`,
  );
}

export function createExamComment(subjectType: string, subjectId: string, body: string, parentCommentId?: string) {
  const payload: Record<string, unknown> = { body };
  if (parentCommentId) payload.parentCommentId = parentCommentId;
  return apiFetch<ExamComment>(
    `${API_V1}/exam/comments/${encodeURIComponent(subjectType)}/${encodeURIComponent(subjectId)}`,
    { method: "POST", body: JSON.stringify(payload) },
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
 * (e.g. `/api/v1/exam/papers/resources/{id}/download`). Prefix the API base so
 * the anchor points at the backend origin.
 */
export function paperResourceDownloadUrl(downloadUrl: string): string {
  if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) return downloadUrl;
  return `${API_BASE}${downloadUrl}`;
}
