import { API_BASE, apiFetch } from "@/lib/api/client";

export interface SourceCatalogItem {
  id: string;
  code: string;
  title: string;
  pricePoints: number;
  accessDays: number;
  questionCount: number;
  duplicationRatePercent: number;
  passRatePercent: number;
  viewCount: number;
  cardColor: string | null;
  coverImageUrl: string | null;
  featured: boolean;
}

export interface SourceCatalogDetail extends SourceCatalogItem {
  description: string | null;
  categorySlug: string | null;
  related: SourceCatalogItem[];
  hasActiveAccess: boolean;
  activeAccessEndsAt: string | null;
}

const PURCHASE_IDEMPOTENCY_PREFIX = "source-purchase-idempotency:";

export interface SourceCatalogPage {
  items: SourceCatalogItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SourcePurchase {
  id: string;
  catalogItemId: string;
  code: string;
  title: string;
  status: string;
  unitPricePoints: number;
  accessDays: number;
  startsAt: string;
  endsAt: string;
  active: boolean;
  createdAt: string;
}

export interface SourcePurchasePage {
  items: SourcePurchase[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SourcePurchaseStats {
  total: number;
  active: number;
  expired: number;
  refunded: number;
}

export interface AdminSourceCatalogItem {
  id: string;
  code: string;
  title: string;
  description: string | null;
  pricePoints: number;
  accessDays: number;
  questionCount: number;
  duplicationRateBp: number;
  passRateBp: number;
  viewCount: number;
  cardColor: string | null;
  categorySlug: string | null;
  active: boolean;
  featured: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminSourcePurchase {
  id: string;
  userId: string;
  username: string | null;
  displayName: string | null;
  catalogItemId: string;
  code: string;
  title: string;
  status: string;
  unitPricePoints: number;
  accessDays: number;
  startsAt: string;
  endsAt: string;
  refunded: boolean;
  paymentLedgerId: string | null;
  refundLedgerId: string | null;
  refundReason: string | null;
  createdAt: string;
}

export interface AdminSourcePurchasePage {
  items: AdminSourcePurchase[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SourceOverview {
  totalPurchases: number;
  activePurchases: number;
  refundedPurchases: number;
  activeCatalogItems: number;
  paidPoints: number;
  refundedPoints: number;
}

export interface AdminQuestionOption {
  id: string;
  optionText: string | null;
  optionImageUrl: string | null;
  isCorrect: boolean;
  sortOrder: number;
}

export interface AdminQuestion {
  id: string;
  catalogItemId: string;
  questionText: string | null;
  questionImageUrl: string | null;
  explanation: string | null;
  multipleCorrect: boolean;
  sortOrder: number;
  options: AdminQuestionOption[];
  createdAt: string;
  updatedAt: string;
}

export interface PublicQuestionOption {
  id: string;
  optionText: string | null;
  optionImageUrl: string | null;
  isCorrect: boolean;
}

export interface PublicQuestion {
  id: string;
  questionText: string | null;
  questionImageUrl: string | null;
  explanation: string | null;
  multipleCorrect: boolean;
  sortOrder: number;
  options: PublicQuestionOption[];
}

export interface QuestionOptionBody {
  optionText?: string;
  optionImageUrl?: string;
  isCorrect?: boolean;
  sortOrder?: number;
}

export interface QuestionBody {
  questionText?: string;
  questionImageUrl?: string;
  explanation?: string;
  sortOrder?: number;
  options: QuestionOptionBody[];
}

export interface AdminSourceCatalogBody {
  code: string;
  title: string;
  description?: string;
  pricePoints: number;
  accessDays?: number;
  questionCount?: number;
  duplicationRateBp?: number;
  passRateBp?: number;
  cardColor?: string;
  coverImageUrl?: string;
  categorySlug?: string;
  active?: boolean;
  featured?: boolean;
  sortOrder?: number;
}

export const SOURCE_PURCHASE_STATUS_LABELS: Record<string, string> = {
  active: "Còn hạn",
  expired: "Hết hạn",
  refunded: "Đã hoàn",
  cancelled: "Đã hủy",
};

export function browseSourceCatalog(opts?: {
  q?: string;
  featured?: boolean;
  sort?: string;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams({
    page: String(opts?.page ?? 0),
    size: String(opts?.size ?? 0),
  });
  if (opts?.q) params.set("q", opts.q);
  if (opts?.featured) params.set("featured", "true");
  if (opts?.sort) params.set("sort", opts.sort);
  return apiFetch<SourceCatalogPage>(`/api/v1/source/catalog?${params}`);
}

export function getFeaturedSource(limit = 8) {
  return apiFetch<SourceCatalogItem[]>(`/api/v1/source/catalog/featured?limit=${limit}`);
}

export function getSourceDetail(idOrCode: string) {
  return apiFetch<SourceCatalogDetail>(`/api/v1/source/catalog/${encodeURIComponent(idOrCode)}`);
}

function resolvePurchaseIdempotencyKey(catalogItemId: string, idempotencyKey?: string): string {
  if (idempotencyKey) return idempotencyKey;
  if (typeof sessionStorage === "undefined") return crypto.randomUUID();
  const storageKey = `${PURCHASE_IDEMPOTENCY_PREFIX}${catalogItemId}`;
  const stored = sessionStorage.getItem(storageKey);
  if (stored) return stored;
  const fresh = crypto.randomUUID();
  sessionStorage.setItem(storageKey, fresh);
  return fresh;
}

export function clearSourcePurchaseIdempotency(catalogItemId: string) {
  if (typeof sessionStorage === "undefined") return;
  sessionStorage.removeItem(`${PURCHASE_IDEMPOTENCY_PREFIX}${catalogItemId}`);
}

export async function purchaseSource(catalogItemId: string, idempotencyKey?: string) {
  const key = resolvePurchaseIdempotencyKey(catalogItemId, idempotencyKey);
  const headers: Record<string, string> = { "Idempotency-Key": key };
  const result = await apiFetch<SourcePurchase>("/api/v1/source/purchases", {
    method: "POST",
    headers,
    body: JSON.stringify({ catalogItemId }),
  });
  clearSourcePurchaseIdempotency(catalogItemId);
  return result;
}

export function listMyPurchases(filter: "active" | "expired" | "all" = "all", page = 0, size = 50) {
  const params = new URLSearchParams({ filter, page: String(page), size: String(size) });
  return apiFetch<SourcePurchasePage>(`/api/v1/source/purchases?${params}`);
}

export function getMyPurchaseStats() {
  return apiFetch<SourcePurchaseStats>("/api/v1/source/purchases/stats");
}

export function getSourceOverview() {
  return apiFetch<SourceOverview>("/api/v1/admin/source/overview");
}

export function listAdminSourceCatalog() {
  return apiFetch<AdminSourceCatalogItem[]>("/api/v1/admin/source/catalog");
}

export function createSourceCatalogItem(body: AdminSourceCatalogBody) {
  return apiFetch<AdminSourceCatalogItem>("/api/v1/admin/source/catalog", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateSourceCatalogItem(id: string, body: Partial<AdminSourceCatalogBody>) {
  return apiFetch<AdminSourceCatalogItem>(`/api/v1/admin/source/catalog/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function deleteSourceCatalogItem(id: string) {
  return apiFetch<void>(`/api/v1/admin/source/catalog/${id}`, { method: "DELETE" });
}

const STORAGE_PUBLIC_BASE =
  process.env.NEXT_PUBLIC_STORAGE_PUBLIC_BASE_URL ?? "http://localhost:9000/fuoverflow-media";

export function sourceMediaUrl(urlOrKey: string | null | undefined): string | null {
  if (!urlOrKey) return null;
  if (urlOrKey.startsWith("http://") || urlOrKey.startsWith("https://")) return urlOrKey;
  if (urlOrKey.startsWith("/uploads/")) return `${API_BASE}${urlOrKey}`;
  return `${STORAGE_PUBLIC_BASE.replace(/\/$/, "")}/${urlOrKey.replace(/^\//, "")}`;
}

export function listAdminQuestions(catalogItemId: string) {
  return apiFetch<AdminQuestion[]>(`/api/v1/admin/source/catalog/${catalogItemId}/questions`);
}

export function createQuestion(catalogItemId: string, body: QuestionBody) {
  return apiFetch<AdminQuestion>(`/api/v1/admin/source/catalog/${catalogItemId}/questions`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateQuestion(catalogItemId: string, questionId: string, body: QuestionBody) {
  return apiFetch<AdminQuestion>(
    `/api/v1/admin/source/catalog/${catalogItemId}/questions/${questionId}`,
    {
      method: "PUT",
      body: JSON.stringify(body),
    },
  );
}

export function deleteQuestion(catalogItemId: string, questionId: string) {
  return apiFetch<void>(
    `/api/v1/admin/source/catalog/${catalogItemId}/questions/${questionId}`,
    { method: "DELETE" },
  );
}

export function reorderQuestions(catalogItemId: string, questionIds: string[]) {
  return apiFetch<void>(`/api/v1/admin/source/catalog/${catalogItemId}/questions/reorder`, {
    method: "PUT",
    body: JSON.stringify({ questionIds }),
  });
}

export interface SourceMediaUploadResult {
  objectKey: string;
  publicUrl: string;
}

export async function uploadSourceMedia(file: File): Promise<SourceMediaUploadResult> {
  const formData = new FormData();
  formData.append("file", file);
  const res = await fetch(`${API_BASE}/api/v1/admin/source/media`, {
    method: "POST",
    credentials: "include",
    body: formData,
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(text || "Upload failed");
  }
  const parsed = JSON.parse(text) as {
    data?: SourceMediaUploadResult;
    success?: boolean;
  };
  if (!parsed.data?.objectKey || !parsed.data?.publicUrl) {
    throw new Error("Upload failed");
  }
  return parsed.data;
}

export function getSourceQuestions(idOrCode: string) {
  return apiFetch<PublicQuestion[]>(
    `/api/v1/source/catalog/${encodeURIComponent(idOrCode)}/questions`,
  );
}

export function listAdminSourcePurchases(opts?: {
  userId?: string;
  catalogItemId?: string;
  status?: string;
  code?: string;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams({
    page: String(opts?.page ?? 0),
    size: String(opts?.size ?? 20),
  });
  if (opts?.userId) params.set("userId", opts.userId);
  if (opts?.catalogItemId) params.set("catalogItemId", opts.catalogItemId);
  if (opts?.status) params.set("status", opts.status);
  if (opts?.code) params.set("code", opts.code);
  return apiFetch<AdminSourcePurchasePage>(`/api/v1/admin/source/purchases?${params}`);
}

export function refundSourcePurchase(id: string, reason?: string) {
  return apiFetch<AdminSourcePurchase>(`/api/v1/admin/source/purchases/${id}/refund`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

export { formatPoints } from "@/lib/format-points";
