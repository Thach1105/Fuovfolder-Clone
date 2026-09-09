import { API_BASE, apiFetch } from "@/lib/api/client";

export interface AdminSubjectLatestPaper {
  examCode: string;
  paperType: ExamPaperType;
  status: ExamPaperStatus;
  term: string | null;
  createdAt: string;
}

export interface AdminSubject {
  id: string;
  code: string;
  title: string;
  description: string | null;
  coverImageUrl: string | null;
  cardColor: string | null;
  categorySlug: string | null;
  fePreviewImageCount: number;
  viewCount: number;
  active: boolean;
  sortOrder: number;
  feQuestionCount: number;
  pePaperCount: number;
  fePaperCount: number;
  pePaperCountAllStatuses: number;
  latestPaper: AdminSubjectLatestPaper | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminSubjectBody {
  code: string;
  title: string;
  description?: string;
  coverImageUrl?: string;
  cardColor?: string;
  categorySlug?: string;
  fePreviewImageCount?: number;
  active?: boolean;
  sortOrder?: number;
}

export interface AdminFeQuestion {
  id: string;
  subjectId: string;
  questionText: string | null;
  questionImageUrls: string[] | null;
  questionBlurUrls: string[] | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface FeQuestionBody {
  questionText?: string;
  questionImageUrls?: string[];
  questionBlurUrls?: string[];
  sortOrder?: number;
}

export interface AdminPeResource {
  id: string;
  folderLabel: string | null;
  objectKey: string;
  originalFilename: string;
  mimeType: string | null;
  sizeBytes: number | null;
  sortOrder: number;
}

export interface AdminPeItem {
  id: string;
  subjectId: string;
  title: string;
  description: string | null;
  examImageUrls: string[] | null;
  sortOrder: number;
  resources: AdminPeResource[];
  createdAt: string;
  updatedAt: string;
}

export interface PeItemBody {
  title: string;
  description?: string;
  examImageUrls?: string[];
  sortOrder?: number;
}

export interface PeResourceBody {
  objectKey: string;
  originalFilename: string;
  mimeType?: string;
  sizeBytes?: number;
  folderLabel?: string;
  sortOrder?: number;
}

// ---------------------------------------------------------------------------
// Subjects
// ---------------------------------------------------------------------------

export function listExamSubjects() {
  return apiFetch<AdminSubject[]>("/api/v1/admin/exam/subjects");
}

export function getExamSubject(id: string) {
  return apiFetch<AdminSubject>(`/api/v1/admin/exam/subjects/${id}`);
}

export function createExamSubject(body: AdminSubjectBody) {
  return apiFetch<AdminSubject>("/api/v1/admin/exam/subjects", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateExamSubject(id: string, body: Partial<AdminSubjectBody>) {
  return apiFetch<AdminSubject>(`/api/v1/admin/exam/subjects/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function deleteExamSubject(id: string) {
  return apiFetch<void>(`/api/v1/admin/exam/subjects/${id}`, { method: "DELETE" });
}

// ---------------------------------------------------------------------------
// FE questions
// ---------------------------------------------------------------------------

export function listExamFeQuestions(subjectId: string) {
  return apiFetch<AdminFeQuestion[]>(`/api/v1/admin/exam/subjects/${subjectId}/fe`);
}

export function createExamFeQuestion(subjectId: string, body: FeQuestionBody) {
  return apiFetch<AdminFeQuestion>(`/api/v1/admin/exam/subjects/${subjectId}/fe`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateExamFeQuestion(subjectId: string, questionId: string, body: FeQuestionBody) {
  return apiFetch<AdminFeQuestion>(
    `/api/v1/admin/exam/subjects/${subjectId}/fe/${questionId}`,
    {
      method: "PUT",
      body: JSON.stringify(body),
    },
  );
}

export function deleteExamFeQuestion(subjectId: string, questionId: string) {
  return apiFetch<void>(`/api/v1/admin/exam/subjects/${subjectId}/fe/${questionId}`, {
    method: "DELETE",
  });
}

export function reorderExamFeQuestions(subjectId: string, ids: string[]) {
  return apiFetch<void>(`/api/v1/admin/exam/subjects/${subjectId}/fe/reorder`, {
    method: "PUT",
    body: JSON.stringify({ ids }),
  });
}

// ---------------------------------------------------------------------------
// PE items + resources
// ---------------------------------------------------------------------------

export function listExamPeItems(subjectId: string) {
  return apiFetch<AdminPeItem[]>(`/api/v1/admin/exam/subjects/${subjectId}/pe`);
}

export function createExamPeItem(subjectId: string, body: PeItemBody) {
  return apiFetch<AdminPeItem>(`/api/v1/admin/exam/subjects/${subjectId}/pe`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateExamPeItem(subjectId: string, itemId: string, body: Partial<PeItemBody>) {
  return apiFetch<AdminPeItem>(`/api/v1/admin/exam/subjects/${subjectId}/pe/${itemId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function deleteExamPeItem(subjectId: string, itemId: string) {
  return apiFetch<void>(`/api/v1/admin/exam/subjects/${subjectId}/pe/${itemId}`, {
    method: "DELETE",
  });
}

export function addExamPeResource(subjectId: string, itemId: string, body: PeResourceBody) {
  return apiFetch<AdminPeItem>(
    `/api/v1/admin/exam/subjects/${subjectId}/pe/${itemId}/resources`,
    {
      method: "POST",
      body: JSON.stringify(body),
    },
  );
}

export function deleteExamPeResource(subjectId: string, itemId: string, resourceId: string) {
  return apiFetch<void>(
    `/api/v1/admin/exam/subjects/${subjectId}/pe/${itemId}/resources/${resourceId}`,
    { method: "DELETE" },
  );
}

// ---------------------------------------------------------------------------
// Media
// ---------------------------------------------------------------------------

export type ExamImagePurpose = "exam_fe_image" | "exam_pe_image";

export interface ExamMediaUploadResult {
  objectKey: string;
  blurObjectKey: string | null;
  publicUrl: string;
}

async function uploadExamMedia(
  file: File,
  purpose: "exam_fe_image" | "exam_pe_image" | "exam_pe_resource",
): Promise<ExamMediaUploadResult> {
  const formData = new FormData();
  formData.append("file", file);
  const res = await fetch(
    `${API_BASE}/api/v1/admin/exam/media?purpose=${encodeURIComponent(purpose)}`,
    {
      method: "POST",
      credentials: "include",
      body: formData,
    },
  );
  const text = await res.text();
  if (!res.ok) {
    throw new Error(text || "Upload failed");
  }
  const parsed = JSON.parse(text) as {
    data?: ExamMediaUploadResult;
    success?: boolean;
  };
  if (!parsed.data?.objectKey || !parsed.data?.publicUrl) {
    throw new Error("Upload failed");
  }
  return parsed.data;
}

export function uploadExamImage(file: File, purpose: ExamImagePurpose) {
  return uploadExamMedia(file, purpose);
}

export function uploadExamResource(file: File) {
  return uploadExamMedia(file, "exam_pe_resource");
}

// --- comment moderation -----------------------------------------------------

export interface AdminExamComment {
  id: string;
  subjectType: "fe_question" | "pe_item";
  subjectId: string;
  examSubjectId: string;
  examSubjectCode: string | null;
  authorUserId: string;
  authorUsername: string | null;
  authorDisplayName: string | null;
  parentCommentId: string | null;
  bodyHtml: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdminExamCommentPage {
  items: AdminExamComment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function listAdminExamComments(opts?: {
  subjectType?: "fe_question" | "pe_item";
  examSubjectId?: string;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams({
    page: String(opts?.page ?? 0),
    size: String(opts?.size ?? 20),
  });
  if (opts?.subjectType) params.set("subjectType", opts.subjectType);
  if (opts?.examSubjectId) params.set("examSubjectId", opts.examSubjectId);
  return apiFetch<AdminExamCommentPage>(`/api/v1/admin/exam/comments?${params}`);
}

export function deleteAdminExamComment(commentId: string) {
  return apiFetch<void>(`/api/v1/admin/exam/comments/${commentId}`, { method: "DELETE" });
}

// --- papers (webhook-ingested + legacy) --------------------------------------

export type ExamPaperType = "FE" | "PE";
export type ExamPaperStatus = "draft" | "published";

export interface AdminPaper {
  id: string;
  subjectId: string;
  paperType: ExamPaperType;
  examCode: string;
  term: string | null;
  retakeLabel: string | null;
  title: string;
  status: ExamPaperStatus;
  ingestSource: string | null;
  questionCount: number;
  resourceCount: number;
  publishedAt: string | null;
  createdAt: string;
  campus: string | null;
}

export interface AdminPaperQuestion {
  id: string;
  questionText: string | null;
  imageUrls: string[];
  blurUrls: string[];
  sortOrder: number;
}

export interface AdminPaperContent {
  paper: AdminPaper;
  questions: AdminPaperQuestion[];
  images: string[];
  resources: AdminPeResource[];
}

export interface AdminWebhookEvent {
  id: string;
  clientId: string;
  eventId: string;
  status: "pending" | "processing" | "done" | "failed";
  attemptCount: number;
  paperId: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  processedAt: string | null;
}

export function listExamPapers(opts?: { subjectId?: string; status?: ExamPaperStatus }) {
  const params = new URLSearchParams();
  if (opts?.subjectId) params.set("subjectId", opts.subjectId);
  if (opts?.status) params.set("status", opts.status);
  const query = params.toString();
  return apiFetch<AdminPaper[]>(`/api/v1/admin/exam/papers${query ? `?${query}` : ""}`);
}

export function getExamPaperContent(paperId: string) {
  return apiFetch<AdminPaperContent>(`/api/v1/admin/exam/papers/${paperId}/content`);
}

export function publishExamPaper(paperId: string) {
  return apiFetch<AdminPaper>(`/api/v1/admin/exam/papers/${paperId}/publish`, { method: "POST" });
}

export function deleteExamPaper(paperId: string) {
  return apiFetch<void>(`/api/v1/admin/exam/papers/${paperId}`, { method: "DELETE" });
}

export function listExamWebhookEvents(status?: AdminWebhookEvent["status"]) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiFetch<AdminWebhookEvent[]>(`/api/v1/admin/exam/webhook-events${query}`);
}

const STORAGE_PUBLIC_BASE = process.env.NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL ?? "";

export function examMediaUrl(urlOrKey: string | null | undefined): string | null {
  if (!urlOrKey) return null;
  if (urlOrKey.startsWith("http://") || urlOrKey.startsWith("https://")) return urlOrKey;
  if (urlOrKey.startsWith("/api/") || urlOrKey.startsWith("/uploads/")) return `${API_BASE}${urlOrKey}`;
  if (STORAGE_PUBLIC_BASE) {
    return `${STORAGE_PUBLIC_BASE.replace(/\/$/, "")}/${urlOrKey.replace(/^\//, "")}`;
  }
  return `${API_BASE}/uploads/${urlOrKey.replace(/^\//, "")}`;
}
