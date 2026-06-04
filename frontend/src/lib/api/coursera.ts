import { apiFetch } from "@/lib/api/client";

export interface CatalogItem {
  id: string;
  code: string;
  title: string;
  description: string | null;
  pricePoints: number;
  featured: boolean;
}

export interface AdminCatalogItem {
  id: string;
  code: string;
  title: string;
  description: string | null;
  pricePoints: number;
  active: boolean;
  featured: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface RequestSummary {
  id: string;
  status: string;
  totalPoints: number;
  catalogCode: string;
  catalogTitle: string;
  username: string | null;
  createdAt: string;
  statusChangedAt: string;
}

export interface RequestDetail {
  id: string;
  status: string;
  totalPoints: number;
  userNotes: string | null;
  courseraEmail: string;
  items: { catalogItemId: string; title: string; unitPricePoints: number; quantity: number }[];
  createdAt: string;
  statusChangedAt: string;
}

export interface RequestStats {
  total: number;
  pending: number;
  inProgress: number;
  completed: number;
  cancelled: number;
}

export interface RequestPage {
  items: RequestSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminRequestDetail extends RequestDetail {
  userId: string;
  username: string;
  displayName: string;
  courseraPassword: string;
  assignedToUserId: string | null;
  allowedNextStatuses: string[];
  refunded: boolean;
  paymentLedgerId: string | null;
  refundLedgerId: string | null;
}

/** Fallback when API omits allowedNextStatuses (older backend). */
export const ALLOWED_STATUS_TRANSITIONS: Record<string, string[]> = {
  pending: ["in_progress", "cancelled"],
  in_progress: ["completed", "cancelled"],
  completed: [],
  cancelled: [],
};

export function resolveAllowedNextStatuses(detail: AdminRequestDetail): string[] {
  if (detail.allowedNextStatuses?.length) {
    return detail.allowedNextStatuses;
  }
  return ALLOWED_STATUS_TRANSITIONS[detail.status] ?? [];
}

export interface CourseraOverview {
  totalRequests: number;
  pending: number;
  inProgress: number;
  completed: number;
  cancelled: number;
  activeCatalogItems: number;
}

export interface CreateRequestPayload {
  catalogItemId: string;
  courseraEmail: string;
  courseraPassword: string;
  userNotes?: string;
}

export function listCatalog(q?: string, featured?: boolean) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (featured) params.set("featured", "true");
  const qs = params.toString();
  return apiFetch<CatalogItem[]>(`/api/v1/coursera/catalog${qs ? `?${qs}` : ""}`);
}

export function createCourseraRequest(payload: CreateRequestPayload, idempotencyKey?: string) {
  const headers: Record<string, string> = {};
  if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;
  return apiFetch<RequestDetail>("/api/v1/coursera/requests", {
    method: "POST",
    headers,
    body: JSON.stringify(payload),
  });
}

export function listMyRequests(status?: string, page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);
  return apiFetch<RequestPage>(`/api/v1/coursera/requests?${params}`);
}

export function getMyRequestStats() {
  return apiFetch<RequestStats>("/api/v1/coursera/requests/stats");
}

export function getCourseraOverview() {
  return apiFetch<CourseraOverview>("/api/v1/admin/coursera/overview");
}

export function listAdminCatalog() {
  return apiFetch<AdminCatalogItem[]>("/api/v1/admin/coursera/catalog");
}

export function createCatalogItem(body: {
  code: string;
  title: string;
  description?: string;
  pricePoints: number;
  active?: boolean;
  featured?: boolean;
  sortOrder?: number;
}) {
  return apiFetch<AdminCatalogItem>("/api/v1/admin/coursera/catalog", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateCatalogItem(
  id: string,
  body: Partial<{
    code: string;
    title: string;
    description: string;
    pricePoints: number;
    active: boolean;
    featured: boolean;
    sortOrder: number;
  }>,
) {
  return apiFetch<AdminCatalogItem>(`/api/v1/admin/coursera/catalog/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function deleteCatalogItem(id: string) {
  return apiFetch<void>(`/api/v1/admin/coursera/catalog/${id}`, { method: "DELETE" });
}

export function listAdminRequests(opts?: {
  userId?: string;
  catalogItemId?: string;
  status?: string;
  period?: string;
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
  if (opts?.period) params.set("period", opts.period);
  return apiFetch<RequestPage>(`/api/v1/admin/coursera/requests?${params}`);
}

export function getAdminRequest(id: string) {
  return apiFetch<AdminRequestDetail>(`/api/v1/admin/coursera/requests/${id}`);
}

export function updateRequestStatus(id: string, status: string, note?: string) {
  return apiFetch<AdminRequestDetail>(`/api/v1/admin/coursera/requests/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status, note }),
  });
}

export { formatPoints, REQUEST_STATUS_LABELS } from "@/lib/format-points";
