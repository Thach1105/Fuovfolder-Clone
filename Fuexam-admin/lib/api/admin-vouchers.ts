import { apiFetch } from "@/lib/api/client";

export interface VoucherResponse {
  id: string;
  code: string;
  description: string | null;
  discountType: "percentage" | "fixed";
  discountValue: number;
  maxDiscountPoints: number | null;
  minOrderPoints: number;
  maxUsage: number;
  usedCount: number;
  maxUsagePerUser: number;
  applicableTypes: string;
  requiredMembershipSlugs: string | null;
  startsAt: string;
  endsAt: string;
  active: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateVoucherBody {
  code: string;
  description?: string;
  discountType: string;
  discountValue: number;
  maxDiscountPoints?: number | null;
  minOrderPoints: number;
  maxUsage: number;
  maxUsagePerUser: number;
  applicableTypes: string;
  requiredMembershipSlugs?: string | null;
  startsAt: string;
  endsAt: string;
}

export function listVouchers(page = 0, size = 100) {
  return apiFetch<PageResponse<VoucherResponse>>(
    `/api/v1/admin/vouchers?page=${page}&size=${size}`
  );
}

export function getVoucher(id: string) {
  return apiFetch<VoucherResponse>(`/api/v1/admin/vouchers/${id}`);
}

export function createVoucher(body: CreateVoucherBody) {
  return apiFetch<VoucherResponse>("/api/v1/admin/vouchers", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateVoucher(id: string, body: CreateVoucherBody) {
  return apiFetch<VoucherResponse>(`/api/v1/admin/vouchers/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export function toggleVoucher(id: string) {
  return apiFetch<VoucherResponse>(`/api/v1/admin/vouchers/${id}/toggle`, {
    method: "PATCH",
  });
}

export function assignVoucherUsers(id: string, userIds: string[]) {
  return apiFetch<void>(`/api/v1/admin/vouchers/${id}/assignments`, {
    method: "POST",
    body: JSON.stringify({ userIds }),
  });
}

export function removeVoucherAssignment(id: string, userId: string) {
  return apiFetch<void>(`/api/v1/admin/vouchers/${id}/assignments/${userId}`, {
    method: "DELETE",
  });
}

export interface VoucherRedemptionResponse {
  id: string;
  voucherId: string;
  userId: string;
  transactionType: string;
  transactionId: string;
  originalPoints: number;
  discountPoints: number;
  finalPoints: number;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function listVoucherRedemptions(id: string, page = 0, size = 20) {
  return apiFetch<PageResponse<VoucherRedemptionResponse>>(
    `/api/v1/admin/vouchers/${id}/redemptions?page=${page}&size=${size}`
  );
}
