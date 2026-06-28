import { apiFetch } from "@/lib/api/client";
import type {
  AdminOrderPageResponse,
  AdminOrderDetailResponse,
  PaymentAnalyticsResponse,
} from "@/types/api";

export function listOrders(params: {
  page?: number;
  size?: number;
  status?: string;
  userId?: string;
  fromDate?: string;
  toDate?: string;
}) {
  const q = new URLSearchParams();
  if (params.page != null) q.set("page", String(params.page));
  if (params.size != null) q.set("size", String(params.size));
  if (params.status) q.set("status", params.status);
  if (params.userId) q.set("userId", params.userId);
  if (params.fromDate) q.set("fromDate", params.fromDate);
  if (params.toDate) q.set("toDate", params.toDate);
  return apiFetch<AdminOrderPageResponse>(`/api/v1/admin/payments/orders?${q}`);
}

export function getOrder(orderId: string) {
  return apiFetch<AdminOrderDetailResponse>(
    `/api/v1/admin/payments/orders/${orderId}`,
  );
}

export function getAnalytics(params: {
  fromDate?: string;
  toDate?: string;
}) {
  const q = new URLSearchParams();
  if (params.fromDate) q.set("fromDate", params.fromDate);
  if (params.toDate) q.set("toDate", params.toDate);
  return apiFetch<PaymentAnalyticsResponse>(
    `/api/v1/admin/payments/analytics?${q}`,
  );
}
